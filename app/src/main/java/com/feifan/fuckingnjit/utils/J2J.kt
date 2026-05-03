package com.feifan.fuckingnjit.utils

import android.webkit.JavascriptInterface
import com.feifan.fuckingnjit.model.User
import java.util.Observable

/**
 * WebView 与原生层之间的 JS 桥接中间件
 *
 * 基于 Observable 观察者模式实现：登录 WebView 页面通过 JS 接口将用户凭据传递到中间件，
 * 登录流程结束后通知观察者（SampleWebViewImpl）完成用户数据的持久化。
 */
@Suppress("unused")
class J2J private constructor() : Observable() {
    private lateinit var user: User

    companion object {
        private val _instance: J2J by lazy { J2J() }

        fun getInstance(): J2J {
            return _instance
        }
    }

    /**
     * JS 接口：设置用户登录凭据
     *
     * 由登录页面的 JS 在用户点击登录按钮时调用。
     *
     * @param id 学号
     * @param password 密码（加密后）
     */
    @JavascriptInterface
    fun setUserCredentials(id: String, password: String) {
        user = User(id = id, password = password)
    }

    /**
     * JS 接口：通知登录完成并重置状态
     *
     * 由教务系统主页面的 JS 调用，通知所有观察者用户数据已就绪。
     */
    @JavascriptInterface
    fun notifyUserChangeAndReset() {
        setChanged()
        notifyObservers(user)
        user = User()
    }

}