package com.feifan.fuckingnjit.utils

import android.webkit.JavascriptInterface
import com.feifan.fuckingnjit.database.UserData
import java.util.Observable

//import leakcanary.AppWatcher

class J2J private constructor() : Observable() {
    private lateinit var user: UserData

    companion object {
        private val _instance: J2J by lazy { J2J() }

        fun getInstance(): J2J {
            return _instance
        }
    }

    @JavascriptInterface
    fun setUserCredentials(id: String, password: String) {
        user = UserData(id = id, password = password)
    }

    @JavascriptInterface
    fun notifyUserChangeAndReset() {
        setChanged()
        notifyObservers(user)
        user = UserData()
    }

}