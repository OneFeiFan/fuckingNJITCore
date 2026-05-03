package com.feifan.fuckingnjit.service.impl

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.utils.AppConfig
import com.feifan.fuckingnjit.utils.J2J
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import com.feifan.fuckingnjit.utils.system.SystemActionHelper
import kotlinx.coroutines.launch
import java.util.Observable
import java.util.Observer


/**
 * 教务系统登录 Activity
 *
 * 全屏展示一个用于完成 WebVPN 登录流程的 WebView，
 * 通过 J2J 中间件与 JS 交互实现登录态的获取与回传。
 */
class SampleWebViewImpl : AppCompatActivity(), Observer {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 打开一个登陆用WebView页面
        webView = WebView(this)
        webView?.apply {
            val instance = J2J.getInstance()
            instance.addObserver(this@SampleWebViewImpl)
            TodayScheduleManager.clearCache() // 所有和课程相关数据全部清空
            addJavascriptInterface(instance, "J2J")//注入中间件
            visibility = View.VISIBLE
            val webSettings = settings
            webSettings.javaScriptEnabled = true
            webSettings.databaseEnabled = true
            webSettings.domStorageEnabled = true
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
            webSettings.javaScriptCanOpenWindowsAutomatically = true //支持通过JS打开新窗口
            webSettings.loadsImagesAutomatically = true //支持自动加载图片
            webSettings.defaultTextEncodingName = "utf-8"//设置编码格式
            webViewClient = SampleWebViewClientImpl()
            loadUrl("https://casb.njit.edu.cn/http/webvpn0ce64a2014465dfe87dac723232b20edd0da6675d44948234864a5c4ff77b278/appShow?appId=5904538791462728")
        }
        setContentView(webView)
    }

    override fun onDestroy() {
        J2J.getInstance().deleteObserver(this)
        releaseWebView()
        AppConfig.endLogin()
        super.onDestroy()
    }

    /**
     * 安全销毁 WebView 及其关联资源
     *
     * 依次执行停止加载、移除父视图、禁用 JS、清除缓存与历史、
     * 移除 JS 接口和 destroy，最后置空引用防止误用。
     */
    private fun releaseWebView() {
        webView?.apply {
            // 停止加载（先停止，再清理）
            stopLoading()
            // 移除父视图（避免父容器持有引用）
            (parent as? ViewGroup)?.removeView(this)
            // 清理资源
            settings.javaScriptEnabled = false
            clearCache(true)
            clearHistory()
            removeJavascriptInterface("J2J")
            // 销毁WebView
            destroy()
        }
        // 置为null，避免后续误用
        webView = null
    }

    @Deprecated("Deprecated in Java")
    override fun update(o: Observable?, arg: Any?) {
        lifecycleScope.launch {
            try {
                Manager.getUserManager().addUser(this@SampleWebViewImpl, arg as User)
                finish() // 确保在主线程执行
            } catch (e: Exception) {
                SystemActionHelper.handleException(this@SampleWebViewImpl, e, "失败")
            }
        }
    }
}