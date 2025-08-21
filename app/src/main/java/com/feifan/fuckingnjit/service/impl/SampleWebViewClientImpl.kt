package com.feifan.fuckingnjit.service.impl

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.feifan.fuckingnjit.service.SampleWebViewClient
import com.feifan.fuckingnjit.utils.Manager

class SampleWebViewClientImpl(private val context: Context, private val webView: WebView) :
    SampleWebViewClient, WebViewClient() {
    // 拦截 URL 加载
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        view.loadUrl(url)
        return true
    }

    // 页面加载完成回调
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
//        if(url == "https://casb.njit.edu.cn/http/webvpn0ce64a2014465dfe87dac723232b20edd0da6675d44948234864a5c4ff77b278/new/index.html"){
//            val cookieManager: CookieManager = CookieManager.getInstance()
//            cookieManager.removeAllCookie()
//        }else
        if(url.startsWith("https://casb.njit.edu.cn/http/webvpnea5e00498bb033e68046c95dbdf6e09fbc127bea836184c80a0792b662ced92f/authserver/login?service=")){
            val userManager = Manager.getUserManager()
            val user = userManager?.getCurrentUser()
            view.evaluateJavascript("""
                (function() {
                getObj("load").onclick = function() {
                    J2J.addUser(document.querySelector("#mobileUsername").value,document.querySelector("#mobilePassword").value)
                }
                document.querySelector("#mobileUsername").value = "${user?.id}"
                document.querySelector("#mobilePassword").value = "${user?.password}"
                
            })();
        """.trimIndent(),null)
        }
        if(url.startsWith("https://casb.njit.edu.cn/enlink/sso/login")){
            Manager.openDialog( "处理SSO登录",context)
            view.evaluateJavascript("""
            (function() {
                document.querySelector(".commonBtn.sso").click();
                J2J.login();
            })();
        """.trimIndent(),null)
        }
        if(url.startsWith("https://casb.njit.edu.cn/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a/jwglxt/xtgl/index_initMenu.html")){
            Manager.openDialog("正在更新用户信息", context)
            Manager.getUserManager()?.updateUserName(view)

//            val cookieManager: CookieManager = CookieManager.getInstance()
        }

    }

    override fun getCaptchaFromWebView(view: WebView) {
        val userManager = Manager.getUserManager()
        val user = userManager?.getCurrentUser()
        view.evaluateJavascript("""
            (function() {
                document.querySelector("#mobileUsername").value = "${user?.id}"
                document.querySelector("#mobilePassword").value = "${user?.password}"
            })();
        """.trimIndent(),null)
    }
}