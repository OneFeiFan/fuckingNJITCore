package com.feifan.fuckingnjit.service.impl

import android.content.Context
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.service.UserManager
import com.feifan.fuckingnjit.utils.EduScheduleConfig
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.academic.ScoreManager
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.fuckingnjit.utils.network.NetworkStatus
import com.feifan.fuckingnjit.utils.system.SystemActionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * 用户管理接口的实现类，采用懒汉式单例模式。
 *
 * 通过 ObjectBox 完成用户数据的本地持久化，
 * 并在添加用户时自动拉取教务系统的学期时间、基础信息、成绩和课表。
 *
 * 典型生命周期：登录成功后调用 [addUser] 完成初始化，
 * 之后通过 [getCurrentUser] / [getUserScores] / [getCurriculum] 等方法访问缓存数据。
 */
@Suppress("unused")
class UserManagerImpl private constructor() : UserManager {
    companion object {
        /** 单例实例，首次调用 getInstance() 时创建 */
        private val instance_: UserManagerImpl by lazy { UserManagerImpl() }

        /**
         * 获取全局唯一的 UserManagerImpl 实例。
         *
         * @return 单例对象
         */
        fun getInstance(): UserManagerImpl = instance_
    }

    /**
     * 切换当前活跃用户并重新进入登录流程。
     *
     * 先将系统配置中的 currentUserId 更新为目标用户，
     * 然后以 relogin=true 启动 WebView 登录页刷新会话。
     *
     * @param context 应用上下文
     * @param id      目标用户的 ID，若在本地数据库中找不到则不做任何操作
     */
    override fun setCurrentUser(context: Context, id: String) {
        val user = AppDataCenter.getAllUsers().find { it.id == id }
        if (user != null) {
            AppDataCenter.updateSystemConfig { it.currentUserId = id }
            // 切换用户后必须重新登录以刷新 Cookie 和会话
            SystemActionHelper.startLogin(context, true)
        }
    }

    /** 获取当前登录用户，无用户时返回空实体 */
    override fun getCurrentUser(): User {
        return AppDataCenter.getCurrentUser() ?: User()
    }

    /**
     * 清除当前活跃用户标记（登出操作）。
     *
     * 仅将系统配置中的 currentUserId 置空，不删除 User 实体本身。
     * 调用后 [getCurrentUser] 将返回空实体。
     */
    fun removeCurrentUser() {
        AppDataCenter.updateSystemConfig { it.currentUserId = "" }
    }

    /**
     * 获取所有本地用户的简要信息列表（供 JS 桥接使用）。
     *
     * 返回 JSON 格式，以用户 ID 为键，包含 name / gpa / current 三个字段，
     * 其中 current 标识该用户是否为当前活跃用户。
     *
     * @param context 应用上下文，用于异常提示
     * @return JSON 字符串，异常时返回空数组
     */
    override fun getAllUsers(context: Context): String {
        val resultList = JSONObject()
        val currentId = AppDataCenter.getSystemConfig().currentUserId
        val userDataList = AppDataCenter.getAllUsers()

        for (userData in userDataList) {
            val temp = JSONObject()
            temp["name"] = userData.name
            temp["gpa"] = userData.gpa
            temp["current"] = userData.id == currentId
            resultList[userData.id] = temp
        }
        return try {
            resultList.toJSONString()
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "获取用户列表失败")
            JSON.toJSONString(intArrayOf())
        }
    }

    /**
     * 从本地数据库中删除指定用户。
     *
     * @param context 应用上下文，用于异常提示
     * @param id      待删除的用户 ID
     * @return 是否删除成功，用户不存在时也返回 true（幂等语义）
     */
    override suspend fun deleteUser(context: Context, id: String): Boolean {
        return try {
            val userDataList = AppDataCenter.getAllUsers()
            // 先在内存列表中查找，避免直接查询 ObjectBox
            val tmp = userDataList.find { it.id == id }
            if (tmp != null) {
                AppDataCenter.deleteUser(tmp)
            }
            true
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "删除用户失败")
            false
        }
    }

    /**
     * 添加新用户并完成完整的初始化流程（核心方法）。
     *
     * @param context 应用上下文
     * @param user    待添加的用户实体，至少需要 id 和密码字段
     */
    override suspend fun addUser(context: Context, user: User) = withContext(Dispatchers.IO) {
        try {
            if (user.id.isEmpty()) throw Exception("用户ID不能为空")

            // 第一步：异步获取学期开始日期并写入系统配置
            (async {
                // 此任务失败不应阻断后续的用户信息和成绩获取
                try {
                    val startDate = Manager.getWebService().getSemesterStartDate(context)
                    val timestamp = LocalDate.parse(startDate)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()

                    AppDataCenter.updateSystemConfig {
                        it.semesterStartDateMs = timestamp
                        it.currentWeek =
                            EduScheduleConfig.calculateCurrentWeek(timestamp)// 登录强制计算当前周
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    SystemActionHelper.handleException(context, e, "获取学期开始日期失败")
                }
            }).await()

            // 第二步：并行获取用户基础信息与全部成绩（与学期日期任务并发执行）
            val userData = (async { Manager.getWebService().getUserData(context) }).await()
            val scores = (async { Manager.getWebService().getAllSorces(context, "", "") }).await()

            // 用户基础信息获取失败则中断整个流程
            if (userData.isEmpty() && userData["code"] != 200) {
                SystemActionHelper.showToast(context, "获取用户信息失败")
                return@withContext
            }

            // 第三步：将服务端返回的数据写入 User 实体
            user.id = (userData["data"] as JSONObject).getString("id")
            user.name = (userData["data"] as JSONObject).getString("name")

            if (scores["code"] == 200) {
                user.scores = scores.getJSONArray("data")
                user.gpa = ScoreManager.calculateAverageGPA(user.scores)
            }

            // 第四步：根据用户偏好决定是否保留密码明文
            if (!user.storePassword) {
                user.password = ""
            }

            // 第五步：持久化到 ObjectBox 并设为当前活跃用户
            AppDataCenter.saveUser(user)
            AppDataCenter.updateSystemConfig { it.currentUserId = user.id }

            // 第六步：强制拉取课表数据填充本地缓存
            getCurriculum(context, true)
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "添加用户失败")
            throw e
        } finally {
            SystemActionHelper.dismissDialog()
        }
    }

    /**
     * 设置是否保存密码到本地
     *
     * 关闭时将立即清除已存储的密码字段。
     *
     * @param enable true 表示允许存储密码
     */
    fun setPasswordStorageEnabled(enable: Boolean) {
        val user = AppDataCenter.getCurrentUser() ?: return
        user.storePassword = enable
        if (!enable) {
            user.password = ""
        }
        AppDataCenter.saveUser(user)
    }

    /**
     * 查询当前用户是否开启了密码本地存储功能。
     *
     * @return true 表示允许存储，用户未登录时默认返回 true
     */
    fun isPasswordStorageEnabled(): Boolean {
        return AppDataCenter.getCurrentUser()?.storePassword ?: true
    }

    /**
     * 获取用户成绩（优先使用缓存，可强制刷新）
     *
     * @param context 应用上下文
     * @param xnm 学年
     * @param xqm 学期
     * @param refresh 是否忽略缓存强制从服务端拉取
     * @return 包含成绩数组的 JSONObject
     */
    suspend fun getUserScores(
        context: Context,
        xnm: String,
        xqm: String,
        refresh: Boolean
    ): JSONObject {
        val userData = AppDataCenter.getCurrentUser() ?: run {
            SystemActionHelper.startLogin(context, true)
            return JSONObject()
        }

        if (userData.scores.isEmpty() || refresh) {
            val tmp = Manager.getWebService().getAllSorces(context, xnm, xqm)
            if (!tmp.isEmpty()) {
                userData.scores = tmp.getJSONArray("data")
                AppDataCenter.saveUser(userData)
            }
        }
        val result = JSONObject()
        result["data"] = userData.scores
        return result
    }

    /**
     * 获取用户课表（优先使用缓存，可强制刷新）
     *
     * @param context 应用上下文
     * @param refresh 是否忽略缓存强制从服务端拉取
     * @return 课表 JSONObject
     */
    suspend fun getCurriculum(context: Context, refresh: Boolean): JSONObject {
        val userData = AppDataCenter.getCurrentUser() ?: run {
            SystemActionHelper.startLogin(context, true)
            return JSONObject()
        }

        if (userData.curriculums.isEmpty() || refresh) {
            val tmp = Manager.getWebService().getCurriculum(context)
            if (!tmp.isEmpty()) {
                userData.curriculums = tmp
                AppDataCenter.saveUser(userData)
            }
        }
        return userData.curriculums
    }

    /**
     * 获取用户学业进度（优先使用缓存，可强制刷新）
     *
     * @param context 应用上下文
     * @param refresh 是否忽略缓存强制从服务端拉取
     * @return 学业进度 JSONObject
     */
    suspend fun getAcademicProgress(context: Context, refresh: Boolean): JSONObject {
        val userData =
            AppDataCenter.getCurrentUser() ?: return NetworkStatus.Unauthorized.toJsonResult()

        if (userData.academicProgress.isEmpty() || refresh) {
            val tmp = Manager.getWebService().getAcademicProgress(context)
            if (!tmp.isEmpty()) {
                userData.academicProgress = tmp
                AppDataCenter.saveUser(userData)
            }
        }
        return userData.academicProgress
    }
}