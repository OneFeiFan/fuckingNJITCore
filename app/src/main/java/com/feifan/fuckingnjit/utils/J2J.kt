package com.feifan.fuckingnjit.utils

import android.webkit.JavascriptInterface
import com.feifan.fuckingnjit.database.UserData

//import leakcanary.AppWatcher

class J2J {
    private lateinit var user: UserData

    init {
        user = UserData()
//        AppWatcher.objectWatcher.expectWeaklyReachable(user, "d登录用user对象")
    }

    @JavascriptInterface
    fun addUser(id: String, password: String) {
        user = UserData()
        user.id = id
        user.password = password
    }

    @JavascriptInterface
    fun login() {
        Manager.getUserManager()?.addUser(user)
        user = UserData()
    }
}