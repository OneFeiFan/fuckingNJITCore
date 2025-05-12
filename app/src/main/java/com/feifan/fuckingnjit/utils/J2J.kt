package com.feifan.fuckingnjit.utils

import android.webkit.JavascriptInterface
import com.feifan.fuckingnjit.Model.User

//import leakcanary.AppWatcher

class J2J {
    private lateinit var user: User
    init {
        user = User()
//        AppWatcher.objectWatcher.expectWeaklyReachable(user, "d登录用user对象")
    }
    @JavascriptInterface
    fun addUser(id: String, password: String){
        user = User()
        user.setId(id)
        user.setPassword(password)
    }
    @JavascriptInterface
    fun login(){
        Manager.getUserManager().addUser(user)
        user = User()
    }
}