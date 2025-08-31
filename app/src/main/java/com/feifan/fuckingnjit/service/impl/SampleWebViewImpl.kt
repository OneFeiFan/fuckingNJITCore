package com.feifan.fuckingnjit.service.impl

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import com.feifan.fuckingnjit.database.UserData
import com.feifan.fuckingnjit.utils.J2J
import com.feifan.fuckingnjit.utils.Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Observable
import java.util.Observer


//import leakcanary.AppWatcher


class SampleWebViewImpl : Activity(), Observer {
    private var webView: WebView? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView?.apply {
            val instance = J2J.getInstance()
            instance.addObserver(this@SampleWebViewImpl)
            addJavascriptInterface(instance, "J2J")
            visibility = View.VISIBLE
            val webSettings = settings
            webSettings.javaScriptEnabled = true
            webSettings.databaseEnabled = true
            webSettings.domStorageEnabled = true
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT //
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
        Manager.endLogin()
        coroutineScope.cancel() // 避免内存泄漏
        super.onDestroy()
    }

    private fun releaseWebView() {
        webView?.apply {
            // 1. 停止加载（先停止，再清理）
            stopLoading()
            // 2. 移除父视图（避免父容器持有引用）
            (parent as? ViewGroup)?.removeView(this)
            // 3. 清理资源
            settings.javaScriptEnabled = false
            clearCache(true)
            clearHistory()
            removeJavascriptInterface("J2J")
            // 5. 销毁WebView
            destroy()
        }
        // 6. 置为null，避免后续误用
        webView = null
    }

    @Deprecated("Deprecated in Java")
    override fun update(o: Observable?, arg: Any?) {
        coroutineScope.launch {
            try {
                Manager.getUserManager()?.addUser(arg as UserData)
                finish() // 确保在主线程执行
            } catch (e: Exception) {
                Manager.handleException(e, "失败")
            }
        }
    }
}