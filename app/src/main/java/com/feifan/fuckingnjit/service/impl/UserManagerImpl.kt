package com.feifan.fuckingnjit.service.impl

//import leakcanary.AppWatcher
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.TypeReference
import com.feifan.fuckingnjit.Model.User
import com.feifan.fuckingnjit.service.UserManager
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.Tools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.security.GeneralSecurityException

class UserManagerImpl : UserManager {

    //    private val masterKey: String
// 在类中定义可控的协程作用域
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var context_: WeakReference<Context> = WeakReference(null)
    private var webViewRef = WeakReference<WebView>(null)
    private val preferences: SharedPreferences
    private var userList = mutableMapOf<String, User>()
    private val EMPTY_USER_JSON = """{
        "id": "",
        "name": "",
        "password": "",
        "current": false
    }"""
    private lateinit var currentUser: String


    constructor(context: Context) {
        try {
            context_ = WeakReference(context)
            preferences = EncryptedSharedPreferences.create(
                "user",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
//            AppWatcher.objectWatcher.expectWeaklyReachable(this, "UserManager")
        } catch (e: GeneralSecurityException) {
            throw SecurityException(
                "Failed to create master key or encrypted shared preferences",
                e
            )
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                currentUser = preferences.getString("current_user", "")?: ""
            } catch (e: Exception) {
                handleException(e, "Failed to load user list from preferences")
            }
        }
        loadUserList()
    }

    private fun loadUserList() {
        // 改用协程替代 Thread
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val userListJson = preferences.getString("userList", "{}")
                userList = JSON.parseObject(
                    userListJson,
                    object : TypeReference<MutableMap<String, User>>() {})
            } catch (e: Exception) {
                handleException(e, "Failed to load user list from preferences")
            }
        }
    }

    override fun addUser(user: User) {
        if (user.getId().isEmpty()) return
        if (userList.containsKey(user.getId())) {
            currentUser = user.getId()
            return
        }
        Manager.showToast("正在添加用户")
        try {
            userList[user.getId()] = user
            currentUser = user.getId()
        } catch (e: Exception) {
            handleException(e, "用户添加失败")
        }
    }

    private suspend fun updateUI() = withContext(Dispatchers.Main) {
        Manager.dismissDialog()
        webViewRef.get()?.visibility = View.GONE
        context_.get()?.sendBroadcast(
            Intent("CLOSE_WEBVIEW_STRING").setPackage(context_.get()?.packageName)
        )
        webViewRef.clear()
    }

    fun updateUserName(view: WebView) {
        webViewRef = WeakReference(view)
        coroutineScope.launch(Dispatchers.IO) {
            try {

                if (userList[currentUser]?.getName()?.isEmpty() == true) {
                    withContext(Dispatchers.Main) {
                        context_.get()?.let { Manager.openDialog("正在更新用户信息", it) }
                    }
                    val userData = Manager.getWebService().getUserData()
                    if (userData.getString("status") != "error") {
                        val data = userData.getJSONObject("data")
                        userList[currentUser]?.setId(data.getString("id"))
                        userList[currentUser]?.setName(data.getString("name"))
                    } else {
//                        withContext(Dispatchers.Main) {
                            Manager.showToast(userData.getString("message"))
//                        }
                    }
                    try {
                        val startDate = Manager.getWebService().getSemesterStartDate()
                        if (!startDate.contains("error")) {
                            userList[currentUser]?.setSemesterStartDate(startDate)
                        } else {
//                            withContext(Dispatchers.Main) {
                                Manager.showToast("获取学期开始日期失败，请稍后重试")
//                            }
                        }
                    } catch (e: Exception) {
//                        withContext(Dispatchers.Main) {
                            Manager.showToast("获取学期开始日期失败，请稍后重试")
//                        }
                    }
                    try {
                        val allSorces = Manager.getWebService().getAllSorces()
                        userList[currentUser]?.setAllSorces(allSorces)
                    } catch (e: Exception) {
//                        withContext(Dispatchers.Main) {
                            Manager.showToast("获取成绩失败，请稍后重试")
//                        }
                    }
                    try {
                        val allSorces = userList[currentUser]?.getAllSorces()
                        userList[currentUser]?.setGPA(
                            Tools.calculateAverageGPA(
                                JSONObject.parseObject(
                                    allSorces
                                )["data"] as JSONArray
                            )
                        )
                    } catch (e: Exception) {
//                        withContext(Dispatchers.Main) {
                            Manager.showToast("获取成绩失败，请稍后重试")
//                        }
                    }
                    withContext(Dispatchers.Main) {

                        preferences.edit {
                            putString("userList", JSON.toJSONString(userList))
                            putString("current_user", currentUser)
                        }
                    }
                }
                updateUI()
            } catch (e: Exception) {
                println("abcdef")
                println(e.message)
                withContext(Dispatchers.Main) {
                    updateUI()
                    handleException(e, "添加用户失败")
                }
            }
        }
    }

    override fun reStoreUserList() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                preferences.edit {
                    putString("userList", JSON.toJSONString(userList))
                }
            } catch (e: Exception) {
                handleException(e, "更新用户列表失败")
            }
        }
    }

    override fun getOriginalPassword(id: String): String {
        return userList[currentUser]?.getPassword()?: ""
    }

    override fun getCurrentUser(): User {
//        Manager.showToast(currentUser)
//        Manager.showToast(userList.containsKey(currentUser).toString())
        return userList[currentUser]?: User()
    }

    fun removeCurrentUser() {
        currentUser = ""
        preferences.edit { remove("current_user") }
    }

    override fun setCurrentUser(id: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
//        Manager.showToast(id)
//        Manager.showToast(userList.containsKey(id).toString())
        if(userList.containsKey(id)){
        currentUser = id
        preferences.edit { putString("current_user", id)}
        Manager.startLogin(true)
        }
    }

    override fun getAllUsers(): String {
        val resultList = JSON.parseObject(JSON.toJSONString(userList), object : TypeReference<MutableMap<String, User>>() {})
        return try {
            resultList[currentUser]?.setCurrent(true)
            for (user in resultList) {
                user.value.setAllSorces("").setPassword("")
            }
            JSON.toJSONString(resultList)
        } catch (e: Exception) {
            println(e.message)
            handleException(e, "Failed to get all users")
            JSON.toJSONString(intArrayOf())
        }
    }

    override suspend fun deleteUser(id: String): Boolean {
        try {
            if(!userList.containsKey(id)){
                return false
            }
            userList.remove(id)
            // 同步写入数据
            val userListJson = JSON.toJSONString(userList)

            // 合并上下文切换
            withContext(Dispatchers.Main) {
                preferences.edit {
                    putString("userList", userListJson)
                    if (id == currentUser) {
                        Manager.logout()
                        remove("current_user")
                    }
                    apply() // 确保异步写入
                }
            }
            return true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                handleException(e, "Failed to delete user")
            }
            return false
        }
    }


    private fun handleException(e: Exception, message: String) {
        e.printStackTrace()
        println(e.message)
        Manager.showToast(message)
    }

}
