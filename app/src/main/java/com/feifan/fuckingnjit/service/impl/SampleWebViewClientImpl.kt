package com.feifan.fuckingnjit.service.impl

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.feifan.fuckingnjit.utils.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.Manager

class SampleWebViewClientImpl() : WebViewClient() {
    // 拦截 URL 加载
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        view.loadUrl(url)
        return true
    }

    // 页面加载完成回调
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        if (url.startsWith("https://casb.njit.edu.cn/http/webvpnea5e00498bb033e68046c95dbdf6e09fbc127bea836184c80a0792b662ced92f/authserver/login")) {
            val userManager = Manager.getUserManager()
            val user = userManager.getCurrentUser()
            view.evaluateJavascript(
                """
                (function() {
                    getObj("load").onclick = function() {
                        J2J.setUserCredentials(document.querySelector("#mobileUsername").value,document.querySelector("#mobilePassword").value)
                    }
                    document.querySelector("#mobileUsername").value = "${user.id}"
                    document.querySelector("#mobilePassword").value = "${user.password}"
                })();
                """.trimIndent(), null
            )
            BaseDataBoxUtils.updateBaseData { it.currentUserId = "" }
        } else if (url.startsWith("https://casb.njit.edu.cn/enlink/sso/login")) {
            Manager.openDialog("处理SSO登录", view.context)
            view.evaluateJavascript(
                """
                (function() {
                    document.querySelector(".commonBtn.sso").click();
                })();
                """.trimIndent(), null
            )
        } else if (url.startsWith("https://casb.njit.edu.cn/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a/jwglxt/xtgl/index_initMenu.html")) {
            Manager.openDialog("正在更新用户信息", view.context)
            view.evaluateJavascript(
                """
                (function() {
                    J2J.notifyUserChangeAndReset();
                })();
                """.trimIndent(), null
            )
        }
    }
}