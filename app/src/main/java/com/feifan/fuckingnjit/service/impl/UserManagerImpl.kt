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

class UserManagerImpl private constructor() : UserManager {
    companion object {
        private val instance_: UserManagerImpl by lazy { UserManagerImpl() }
        fun getInstance(): UserManagerImpl = instance_
    }

    override fun setCurrentUser(context: Context, id: String) {
        // 查找用户是否存在
        val user = AppDataCenter.getAllUsers().find { it.id == id }
        if (user != null) {
            AppDataCenter.updateSystemConfig { it.currentUserId = id }
            // 切换当前用户必需重新登录
            SystemActionHelper.startLogin(context, true)
        }
    }

    override fun getCurrentUser(): User {
        return AppDataCenter.getCurrentUser() ?: User()
    }

    fun removeCurrentUser() {
        AppDataCenter.updateSystemConfig { it.currentUserId = "" }
    }

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
            SystemActionHelper.handleException(context, e, "Failed to get all users")
            JSON.toJSONString(intArrayOf())
        }
    }

    override suspend fun deleteUser(context: Context, id: String): Boolean {
        return try {
            val userDataList = AppDataCenter.getAllUsers()
            val tmp = userDataList.find { it.id == id }//使用list找也许比在ObjectBox更快
            if (tmp != null) {
                AppDataCenter.deleteUser(tmp)
            }
            true
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "Failed to delete user")
            false
        }
    }

    override suspend fun addUser(context: Context, user: User) = withContext(Dispatchers.IO) {
        try {
            if (user.id.isEmpty()) throw Exception("用户ID不能为空")

            // 获取并保存学期时间
            (async {
                // 防止网络请求问题导致添加用户提前中断
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

            // 获取用户基础信息与成绩
            val userData = (async { Manager.getWebService().getUserData(context) }).await()
            val scores = (async { Manager.getWebService().getAllSorces(context, "", "") }).await()

            // 获取不到用户数据直接中断
            if (userData.isEmpty() && userData["code"] != 200) {
                SystemActionHelper.showToast(context, "获取用户信息失败")
                return@withContext
            }

            // 更新实体字段
            user.id = (userData["data"] as JSONObject).getString("id")
            user.name = (userData["data"] as JSONObject).getString("name")

            if (scores["code"] == 200) {
                user.scores = scores.getJSONArray("data")
                user.gpa = ScoreManager.calculateAverageGPA(user.scores)
            }

            // 处理密码存储偏好
            if (!user.storePassword) {
                user.password = ""
            }

            // 统一保存并切换当前用户
            AppDataCenter.saveUser(user)
            AppDataCenter.updateSystemConfig { it.currentUserId = user.id }

            // 强制拉取一次课程数据作为缓存
            getCurriculum(context, true)
        } catch (e: Exception) {
            SystemActionHelper.handleException(context, e, "添加用户失败")
            throw e
        } finally {
            SystemActionHelper.dismissDialog()
        }
    }

    // 切换密码储存状态则必须强制清除密码
    fun setPasswordStorageEnabled(enable: Boolean) {
        val user = AppDataCenter.getCurrentUser() ?: return
        user.storePassword = enable
        if (!enable) {
            user.password = ""
        }
        AppDataCenter.saveUser(user)
    }

    fun isPasswordStorageEnabled(): Boolean {
        return AppDataCenter.getCurrentUser()?.storePassword ?: true
    }

    // 获取缓存的用户成绩
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

    // 获取缓存的用户课表
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

    // 获取缓存的用户学业进度
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