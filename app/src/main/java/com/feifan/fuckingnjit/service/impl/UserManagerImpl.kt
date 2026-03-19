package com.feifan.fuckingnjit.service.impl

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.service.UserManager
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.NetworkStatus
import com.feifan.fuckingnjit.utils.TimeManager
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.UserBoxUtils
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

    override fun setCurrentUser(id: String) {
        if (UserBoxUtils.getUserById(id) != null) {
            BaseDataBoxUtils.updateBaseData { it.currentUserId = id }
            Manager.startLogin(true)
        }
    }

    override fun getCurrentUser(): User {
        return UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId()) ?: User()
    }

    fun removeCurrentUser() {
        BaseDataBoxUtils.updateBaseData { it.currentUserId = "" }
    }

    override fun getAllUsers(): String {
        val resultList = JSONObject()
        val userDataList = UserBoxUtils.getAllUser()
        for (userData in userDataList) {
            val temp = JSONObject()
            temp["name"] = userData.name
            temp["gpa"] = userData.gpa
            temp["current"] = userData.id == BaseDataBoxUtils.getCurrentUserId()
            resultList[userData.id] = temp
        }
        return try {
            resultList.toJSONString()
        } catch (e: Exception) {
            println(e.message)
            Manager.handleException(e, "Failed to get all users")
            JSON.toJSONString(intArrayOf())
        }
    }

    override suspend fun deleteUser(id: String): Boolean {
        return try {
            val tmp = UserBoxUtils.getUserById(id)
            if (tmp != null) {
                UserBoxUtils.deleteUser(tmp)
            }
            true
        } catch (e: Exception) {
            Manager.handleException(e, "Failed to delete user")
            false
        }
    }

    override suspend fun addUser(user: User) = withContext(Dispatchers.IO) {
        try {
            if (user.id.isEmpty()) throw Exception("用户ID不能为空")
            // 等待学期日期处理完成（如果有）
            (async {
                try {
                    val startDate = Manager.getWebService().getSemesterStartDate()
                    val timestamp = LocalDate.parse(startDate)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    BaseDataBoxUtils.updateBaseData {
                        it.semesterStartDate = timestamp
                        it.currentWeek = TimeManager.getInstance().calculateCurrentWeek(timestamp)
                    }
                } catch (e: Exception) {
                    Manager.handleException(e, "获取学期开始日期失败")
                }
            }).await()
            val userData = (async { Manager.getWebService().getUserData() }).await()
            val scores = (async { Manager.getWebService().getAllSorces("", "") }).await()

            if (userData.isEmpty()) {
                Manager.showToast("获取用户信息失败")
                return@withContext
            }

            // 更新用户数据
            user.id = userData.getString("id")
            user.name = userData.getString("name")
            println(scores.toJSONString())
            user.scores = scores.getJSONArray("data")
            user.gpa = Tools.calculateAverageGPA(user.scores)
            if (!isPasswordStorageEnabled()) {
                user.password = ""
            }

            // 更新存储
            UserBoxUtils.updateUser(user)
            BaseDataBoxUtils.updateBaseData { it.currentUserId = user.id }
            getCurriculum(true)
        } catch (e: Exception) {
            Manager.handleException(e, "添加用户失败")
            throw e  // 重新抛出异常，让调用方知道失败
        } finally {
            Manager.dismissDialog()
        }
    }

    /**
     * 设置是否存储用户密码
     * @param enable true表示存储密码，false表示不存储
     */
    fun setPasswordStorageEnabled(enable: Boolean) {
        BaseDataBoxUtils.updateBaseData { it.storePassword = enable }

        // 如果不存储密码，立即清除已存储的密码
        if (!enable) {
            clearStoredPasswords()
        }
    }

    /**
     * 检查是否启用了密码存储
     * @return Boolean 是否存储密码
     */
    fun isPasswordStorageEnabled(): Boolean {
        return BaseDataBoxUtils.getStorePassword() // 默认值为true，表示默认存储密码
    }

    /**
     * 清除所有已存储的用户密码
     */
    private fun clearStoredPasswords() {
        val userDataList = UserBoxUtils.getAllUser()
        for (userData in userDataList) {
            userData.password = ""
            UserBoxUtils.updateUser(userData)
        }
    }

    suspend fun getUserScores(xnm: String, xqm: String, refresh: Boolean): String {
        val userData = UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId())
        if (userData == null) {
            Manager.startLogin(true)
            Manager.showToast("需要登录")
            return "{}"
        }

        if (userData.scores.isEmpty() || refresh) {
            val tmp = Manager.getWebService().getAllSorces(xnm, xqm)
            if (!tmp.isEmpty()) {
                userData.scores = tmp.getJSONArray("data")
                UserBoxUtils.updateUser(userData)
            }
        }
        val result = JSONObject()
        result["data"] = userData.scores
        return result.toJSONString()
    }

    suspend fun getCurriculum(refresh: Boolean): String {
        try {
            val userData = UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId())
            if (userData == null) {
                Manager.startLogin(true)
                Manager.showToast("需要登录")
                return "{}"
            }
            if (userData.curriculums.isEmpty() || refresh) {
                val tmp = Manager.getWebService().getCurriculum()
                if (!tmp.isEmpty()) {
                    userData.curriculums = tmp
                    UserBoxUtils.updateUser(userData)
                }
            }
            return userData.curriculums.toJSONString()
        } catch (e: Exception) {
            Manager.handleException(e, "获取课程表失败")
            return "{}"
        }
    }

    suspend fun getAcademicProgress(refresh: Boolean): JSONObject {
        try {
            val userData = UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId())
            if (userData == null) {
                Manager.startLogin(true)
                Manager.showToast("需要登录")
                return NetworkStatus.Unauthorized.toJsonResult()
            }
            if (userData.academicProgress.isEmpty() || refresh) {
                val tmp = Manager.getWebService().getAcademicProgress()
                if (!tmp.isEmpty()) {
                    userData.academicProgress = tmp
                    UserBoxUtils.updateUser(userData)
                }
            }
            return userData.academicProgress
        } catch (e: Exception) {
            Manager.handleException(e, "获取学业进度失败")
            return NetworkStatus.UnknownError.toJsonResult()
        }
    }
}

