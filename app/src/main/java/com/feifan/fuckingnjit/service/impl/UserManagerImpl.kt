package com.feifan.fuckingnjit.service.impl

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.TypeReference
import com.feifan.fuckingnjit.Model.User
import com.feifan.fuckingnjit.service.UserManager
import com.feifan.fuckingnjit.utils.Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.security.GeneralSecurityException
import androidx.core.content.edit

class UserManagerImpl : UserManager {

    // 在类中定义可控的协程作用域
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var context: Context
    private var webViewRef = WeakReference<WebView>(null)
    private lateinit var preferences: SharedPreferences
    private var userList = hashMapOf<String, User>()
    private lateinit var currentUser: String

    companion object {
        private const val PREF_STORE_PASSWORD = "store_password"
    }

    constructor(context: Context) {
        try {
            this.context = context
            preferences = context.getSharedPreferences("USER", Context.MODE_PRIVATE)
        } catch (e: GeneralSecurityException) {
            Manager.handleException(
                e,
                "Failed to create master key or encrypted shared preferences"
            )
        }
//        coroutineScope.launch(Dispatchers.IO) {
            try {
                currentUser = preferences.getString("current_user", "")?: ""
            } catch (e: Exception) {
                Manager.handleException(e, "Failed to load user list from preferences")
            }
//        }
        try {
            val userListJson = preferences.getString("userList", null)?:"{}"
            userList = JSON.parseObject(
                userListJson,
                object : TypeReference<HashMap<String, User>>() {})
            Manager.getSemesterStartDate(this)
            Manager.registerTimeReceiver()
            Manager.updateTimetableData(this)
        } catch (e: Exception) {
            Manager.handleException(e, "Failed to load user list from preferences")
        }
    }

    override fun addUser(user: User) {
        try {
            val userToAdd = if (!isPasswordStorageEnabled()) {
                user.setPassword("")
            } else {
                user
            }
        if (userToAdd.getId().isEmpty()) return
//        if (userList.containsKey(userToAdd.getId())) {
//            currentUser = userToAdd.getId()
//            return
//        }
        Manager.showToast("请稍后")
            // 如果不存储密码，先清空用户密码
            userList[userToAdd.getId()] = userToAdd
            currentUser = userToAdd.getId()
        } catch (e: Exception) {
            Manager.handleException(e, "用户添加失败")
        }
    }

    override fun setCurrentUser(id: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        if (userList.containsKey(id)) {
            currentUser = id
            preferences.edit{ putString("current_user", id) }
            Manager.startLogin(true)
        }
    }

    override fun getCurrentUser(): User {
        return userList[currentUser] ?: User()
    }

    override fun getOriginalPassword(id: String): String {
        return userList[currentUser]?.getPassword() ?: ""
    }

    override fun getAllUsers(): String {
        val resultList = JSON.parseObject(
            JSON.toJSONString(userList),
            object : TypeReference<MutableMap<String, User>>() {})
        return try {
            resultList[currentUser]?.setCurrent(true)
            for (user in resultList) {
                user.value.setAllSorces("").setPassword("")
            }
            JSON.toJSONString(resultList)
        } catch (e: Exception) {
            println(e.message)
            Manager.handleException(e, "Failed to get all users")
            JSON.toJSONString(intArrayOf())
        }
    }

    override suspend fun deleteUser(id: String): Boolean {
        return try {
            if (!userList.containsKey(id)) return false
            userList.remove(id)
            val userListJson = JSON.toJSONString(userList)
            withContext(Dispatchers.Main) {
                if (id == currentUser) {
                    Manager.logout()
                    preferences.edit{remove("current_user")}
                }
                preferences.edit{putString("userList", userListJson)}
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Manager.handleException(e, "Failed to delete user")
            }
            false
        }
    }

    override fun reStoreUserList() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                println("更新用户列表")
                println(userList)
                preferences.edit{putString("userList", JSON.toJSONString(userList))}
            } catch (e: Exception) {
                Manager.handleException(e, "更新用户列表失败")
            }
        }
    }

    fun updateUserName(view: WebView) {
        webViewRef = WeakReference(view)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    Manager.openDialog("正在更新用户信息", context)
                }
                if (userList[currentUser]?.getName()?.isEmpty() == true) {

                    val userData = Manager.getWebService().getUserData()
                    if (userData.getString("status") != "error") {
                        val data = userData.getJSONObject("data")
                        userList[currentUser]?.setId(data.getString("id"))
                        userList[currentUser]?.setName(data.getString("name"))
                    } else {
                        Manager.showToast(userData.getString("message"))
                    }
                    try {
                        val startDate = Manager.getWebService().getSemesterStartDate()
                        if (!startDate.contains("error")) {
                            userList[currentUser]?.setSemesterStartDate(startDate)
                        } else {
                            Manager.showToast("获取学期开始日期失败，请稍后重试")
                        }
                    } catch (e: Exception) {
                        Manager.showToast("获取学期开始日期失败，请稍后重试")
                    }
                    try {
                        val allSorces = Manager.getWebService().getAllSorces(false)
                        userList[currentUser]?.setAllSorces(allSorces)
                    } catch (e: Exception) {
                        Manager.showToast("获取成绩失败，请稍后重试")
                    }
                }
                withContext(Dispatchers.Main) {
                    preferences.edit{putString("userList", JSON.toJSONString(userList))}
                    preferences.edit{putString("current_user", currentUser)}
                }
                updateUI()
            } catch (e: Exception) {
                println("abcdef")
                println(e.message)
                withContext(Dispatchers.Main) {
                    updateUI()
                    Manager.handleException(e, "添加用户失败")
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

    fun removeCurrentUser() {
        currentUser = ""
        preferences.edit{remove("current_user")}
    }

    /**
     * 设置是否存储用户密码
     * @param enable true表示存储密码，false表示不存储
     */
    fun setPasswordStorageEnabled(enable: Boolean) {
        preferences.edit {
            putBoolean(PREF_STORE_PASSWORD, enable)
        }

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
        return preferences.getBoolean(PREF_STORE_PASSWORD, true) // 默认值为true，表示默认存储密码
    }

    /**
     * 清除所有已存储的用户密码
     */
    private fun clearStoredPasswords() {
        userList.values.forEach { user ->
            user.setPassword("") // 清空密码
        }
        reStoreUserList() // 更新持久化存储
    }
}
