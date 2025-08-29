package com.feifan.fuckingnjit.service.impl

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import com.feifan.fuckingnjit.service.SampleWebView
import com.feifan.fuckingnjit.utils.J2J
import com.feifan.fuckingnjit.utils.Manager

//import leakcanary.AppWatcher


class SampleWebViewImpl : SampleWebView, Activity() {
    private var webView: WebView? = null
    private lateinit var destroyReceiver: BroadcastReceiver

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setResult(RESULT_OK, null);  // 返回成功状态和数据
//        Toast.makeText(this, "SampleWebViewImpl onCreate", Toast.LENGTH_SHORT).show()

        webView = WebView(this)
        webView?.addJavascriptInterface(J2J(), "J2J")
        webView!!.visibility = View.VISIBLE
        val webSettings = webView!!.settings
        webSettings.javaScriptEnabled = true
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT //
        webSettings.javaScriptCanOpenWindowsAutomatically = true //支持通过JS打开新窗口
        webSettings.loadsImagesAutomatically = true //支持自动加载图片
        webSettings.defaultTextEncodingName = "utf-8"//设置编码格式

        webView?.webViewClient = SampleWebViewClientImpl(this, webView!!)
        setContentView(webView)
//        addContentView(
//            webView, ViewGroup.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT
//            )
//        )
        webView?.loadUrl("https://casb.njit.edu.cn/http/webvpn0ce64a2014465dfe87dac723232b20edd0da6675d44948234864a5c4ff77b278/appShow?appId=5904538791462728")
//        AppWatcher.objectWatcher.expectWeaklyReachable(webView!!,"webView")

//        webView?.webViewClient = MyWebViewClient(this, webView!!)
    }


//    override fun onPause() {
//        super.onPause()
//        unregisterReceiver(destroyReceiver)
//    }

    override fun openWebView() {
        webView!!.visibility = View.VISIBLE
    }

    override fun closeWebView() {
        webView!!.visibility = View.GONE
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        destroyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                finish()
            }
        }

        val filter = IntentFilter("CLOSE_WEBVIEW_STRING")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(destroyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(destroyReceiver, filter)
        }
//        AppWatcher.objectWatcher.expectWeaklyReachable(destroyReceiver,"destroyReceiver")
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(destroyReceiver) // 确保及时注销
//        Toast.makeText(this, "SampleWebViewImpl onPause", Toast.LENGTH_SHORT).show()
    }
//
//    override fun onRestart() {
//        super.onRestart()
////        val intent_ = Intent(this, SampleWebViewImpl::class.java)
////        intent_.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
////        startActivityForResult(intent_, PICK_IMAGE_REQUEST)
////        Toast.makeText(this, "SampleWebViewImpl onRestart", Toast.LENGTH_SHORT).show()
//    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        Toast.makeText(this, "SampleWebViewImpl onActivityResult", Toast.LENGTH_SHORT).show()
//        Toast.makeText(this, (resultCode).toString(), Toast.LENGTH_SHORT).show()
//        if (requestCode == PICK_IMAGE_REQUEST) {
//            finish()
//        }
//    }

    override fun onDestroy() {
//        Manager.dismissDialog()
        releaseWebView()
        Manager.endLogin()
        super.onDestroy()
    }

//    override fun onSaveInstanceState(outState: Bundle) {
//        Toast.makeText(this, "SampleWebViewImpl onSaveInstanceState", Toast.LENGTH_SHORT).show()
//        Manager.startLogin()
//        super.onSaveInstanceState(outState)
//    }

    @SuppressLint("JavascriptInterface")
    override fun releaseWebView() {
        webView?.apply {
            // 先移除父View再销毁
            (parent as? ViewGroup)?.removeView(this)

            // 按正确顺序清理资源
            settings.javaScriptEnabled = false
            clearCache(true)
            clearHistory()
            removeJavascriptInterface("Android")
            stopLoading()
//            webViewClient = null

            // 最后销毁
            destroy()
            webView = null
        }
    }
}