package com.feifan.fuckingnjit.service.impl

import android.content.Context
import android.content.Intent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.database.UserData
import com.feifan.fuckingnjit.service.UserManager
import com.feifan.fuckingnjit.utils.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.SecureUtil
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.UserBoxUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.ZoneId

class UserManagerImpl : UserManager {

    // 在类中定义可控的协程作用域
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var context: Context
    private var webViewRef = WeakReference<WebView>(null)

    constructor(context: Context) {
        this.context = context
        try {
            Manager.registerTimeReceiver()
        } catch (e: Exception) {
            Manager.handleException(e, "Failed to load user list from preferences")
        }
    }


    override fun setCurrentUser(id: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        if (UserBoxUtils.getUserById(id) != null) {
            BaseDataBoxUtils.updateBaseData { it.currentUserId = id }
//            preferences.edit { putString("current_user", id) }
            Manager.startLogin(true)
        }
    }

    override fun getCurrentUser(): UserData {
        return UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId()) ?: UserData()
    }

    fun removeCurrentUser() {
        BaseDataBoxUtils.updateBaseData { it.currentUserId = "" }
    }
//    override fun getOriginalPassword(id: String): String {
//        val userData = UserBoxUtils.getUserById(id)
//
//        SecureUtil.rsaDecrypt(userData?.password ?: "")
//        return userList[currentUser]?.getPassword() ?: ""
//    }

    override fun getAllUsers(): String {
        val resultList = JSONArray()
        val userDataList = UserBoxUtils.getAllUserData()
        for (userData in userDataList) {
            val temp = JSONObject()
            temp["id"] = userData.id
            temp["name"] = userData.name
            temp["gpa"] = userData.gpa
            if (userData.id == BaseDataBoxUtils.getCurrentUserId()) {
                temp["current"] = true
            } else {
                temp["current"] = false
            }
            resultList.add(temp)
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
                UserBoxUtils.deleteUserData(tmp)
            }
            true
        } catch (e: Exception) {
            Manager.handleException(e, "Failed to delete user")
            false
        }
    }

    override fun reStoreUserList() {
        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                println("更新用户列表")
//                println(userList)
//                preferences.edit { putString("userList", JSON.toJSONString(userList)) }
//            } catch (e: Exception) {
//                Manager.handleException(e, "更新用户列表失败")
//            }
        }
    }

    override fun addUser(user: UserData) {
        try {
            var userData = UserBoxUtils.getUserById(user.id)
            if (userData == null) {
                userData = UserData(id = user.id, password = SecureUtil.rsaEncrypt(user.password))
            }
            if (user.id.isEmpty()) return
            val userToAdd = if (!isPasswordStorageEnabled()) {
                userData.password = ""
                userData
            } else {
                userData
            }

            Manager.showToast("请稍后")
            // 如果不存储密码，先清空用户密码
//            userList[userToAdd.getId()] = userToAdd
            UserBoxUtils.insertUserData(userToAdd)
            BaseDataBoxUtils.updateBaseData { it.currentUserId = userToAdd.id }
        } catch (e: Exception) {
            Manager.handleException(e, "用户添加失败")
        }
    }

    fun updateUserName(view: WebView) {
        webViewRef = WeakReference(view)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    Manager.openDialog("正在更新用户信息", context)
                }
                val currentUser = BaseDataBoxUtils.getCurrentUserId()
                val user = UserBoxUtils.getUserById(currentUser)
                if (user?.name?.isEmpty() == true) {

                    val data = Manager.getWebService().getUserData()
                    if (!data.isEmpty()) {
                        user.id = data.getString("id")
                        user.name = data.getString("name")
                    }

                    val startDate = Manager.getWebService().getSemesterStartDate()
                    if (!startDate.contains("error")) {
                        val localDate = LocalDate.parse(startDate)
                        val zonedDateTime = localDate.atStartOfDay(ZoneId.systemDefault())
                        val timestamp = zonedDateTime.toInstant().toEpochMilli()

                        BaseDataBoxUtils.updateBaseData {
                            it.semesterStartDate = timestamp
                            it.currentWeek =
                                Manager.getTimeManager().calculateCurrentWeek(timestamp)
                        }
                    } else {
                        Manager.showToast("获取学期开始日期失败，请稍后重试")
                    }
                    val allSorces = Manager.getWebService().getAllSorces()
                    println(allSorces.toJSONString())
                    user.scores = allSorces.getJSONArray("data")
                    user.gpa = Tools.calculateAverageGPA(user.scores)
                    UserBoxUtils.updateUserData(user)
                }

                updateUI()
            } catch (e: Exception) {
                Manager.handleException(e, "添加用户失败")
                withContext(Dispatchers.Main) {
                    updateUI()
                }
            }
        }
    }

    private suspend fun updateUI() = withContext(Dispatchers.Main) {
        Manager.dismissDialog()
        webViewRef.get()?.visibility = View.GONE
        context.sendBroadcast(Intent("CLOSE_WEBVIEW_STRING").setPackage(context.packageName))
        webViewRef.clear()
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
        val userDataList = UserBoxUtils.getAllUserData()
        for (userData in userDataList) {
            userData.password = ""
            UserBoxUtils.updateUserData(userData)
        }
    }

    suspend fun getUserScores(refresh: Boolean): String {
        val userData = UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId())
        if (userData == null) {
            Manager.startLogin(true)
            Manager.showToast("需要登录")
            return "{}"
        }

        if (userData.scores.isEmpty() || refresh) {
            val tmp = Manager.getWebService().getAllSorces()
            if (!tmp.isEmpty()) {
                userData.scores = tmp.getJSONArray("data")
                UserBoxUtils.updateUserData(userData)
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
                    UserBoxUtils.updateUserData(userData)
                }
            }
            return userData.curriculums.toJSONString()
        } catch (e: Exception) {
            Manager.handleException(e, "获取课程表失败")
            return "{}"
        }
    }

}

