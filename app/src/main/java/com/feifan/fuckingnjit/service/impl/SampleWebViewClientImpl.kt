package com.feifan.fuckingnjit.service.impl

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.fuckingnjit.utils.system.SystemActionHelper

/**
 * 登录流程 WebViewClient 实现
 *
 * 拦截教务系统登录过程中的关键 URL 跳转，
 * 在对应页面自动注入账号密码、触发 SSO 登录和用户信息同步。
 */
class SampleWebViewClientImpl() : WebViewClient() {
    /**
     * 拦截所有 URL 加载请求并交由 WebView 自行处理
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        view.loadUrl(url)
        return true
    }

    /**
     * 页面加载完成后的回调处理
     *
     * 根据目标 URL 匹配不同阶段：
     * - 登录页：自动填充账号密码
     * - SSO 登录页：点击统一认证按钮
     * - 教务主页：触发用户信息更新
     */
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
        } else if (url.startsWith("https://casb.njit.edu.cn/enlink/sso/login")) {
            AppDataCenter.updateSystemConfig {
                it.currentUserId = ""
            }// 如果是不同用户在登录，到这一步就可以清空旧账号登录状态了
            SystemActionHelper.openDialog("处理SSO登录", view.context)
            view.evaluateJavascript(
                """
                (function() {
                    document.querySelector(".commonBtn.sso").click();
                })();
                """.trimIndent(), null
            )
        } else if (url.startsWith("https://casb.njit.edu.cn/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a/jwglxt/xtgl/index_initMenu.html")) {
            SystemActionHelper.openDialog("正在更新用户信息", view.context)
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