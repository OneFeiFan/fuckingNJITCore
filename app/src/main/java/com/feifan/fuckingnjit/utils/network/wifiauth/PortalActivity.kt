package com.feifan.fuckingnjit.utils.network.wifiauth

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.CaptivePortal
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import com.feifan.fuckingnjit.utils.AppConfig
import com.feifan.fuckingnjit.utils.system.SystemActionHelper
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class PortalActivity : Activity() {

    // 你的目标认证服务器地址
    private val TARGET_CHECK_URL = "http://172.31.255.156"
    private val TAG = "PortalCheck"

    private val MAX_RETRY_COUNT = 2
    private var httpClient: OkHttpClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var mCaptivePortal: CaptivePortal? = null

    private val httpClientInstance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private fun getHttpClient(): OkHttpClient {
        return httpClient ?: synchronized(this) {
            httpClient ?: httpClientInstance.also { httpClient = it }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val captivePortal =
            intent.getParcelableExtra<Parcelable>("android.net.extra.CAPTIVE_PORTAL") as CaptivePortal
        this.mCaptivePortal = captivePortal
        SystemActionHelper.openDialog("正在处理目标网络...", this)
        val network: Network? = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)

        if (network == null) {
            Log.e(TAG, "Network 为空，无法处理，转交系统")
            forwardToSystemComponent()
            return
        }

        // 开启主动探测
        checkTargetServerDirectly(network)
    }

    private fun checkTargetServerDirectly(network: Network) {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.bindProcessToNetwork(network)
        executor.execute {
            var isTargetNetwork = false

            try {
                Log.d(TAG, "尝试直接连接目标: $TARGET_CHECK_URL")

                // 1. 必须使用 network.openConnection 确保走 WiFi 通道
                val urlObj = URL(TARGET_CHECK_URL)
                val connection = network.openConnection(urlObj) as HttpURLConnection

                // 2. 设置极短的超时时间
                // 如果在内网，连接这个 IP 应该非常快 (50-100ms)
                // 如果不在内网，这个私有 IP 是不通的，我们不想等太久
                connection.connectTimeout = 2000 // 2秒超时
                connection.readTimeout = 2000
                connection.requestMethod = "HEAD" // 我们只需要探一下头，不需要下载页面

                // 3. 发起连接
                connection.connect()
                val responseCode = connection.responseCode

                Log.d(TAG, "目标服务器响应码: $responseCode")

                // 只要服务器有响应（无论是 200, 302, 403...），说明我们就在这个内网里！
                // 只有 ConnectException/TimeoutException 才说明不在。
                if (responseCode == 200) {
                    isTargetNetwork = true
                }

                connection.disconnect()

            } catch (e: Exception) {
                Log.d(TAG, "连接目标失败 ($e)，说明不是目标网络")
                isTargetNetwork = false
            }

            // 回到主线程分发结果
            handler.post {
                if (isTargetNetwork) {
                    // === 命中！是我们要拦截的网络 ===
                    handleMyTargetNetwork()
                } else {
                    // === 没命中！转交给系统 ===
                    forwardToSystemComponent()
                }
            }
        }
    }

    private fun handleMyTargetNetwork() {
        Log.i(TAG, "确认是目标网络，开始拦截处理")

        // 1. 绑定进程到网络

        val type = AppConfig.getWifiAuthType()
        val user = AppDataCenter.getCurrentUser()
        val url =
            "http://172.31.255.156:801/eportal/portal/login?login_method=1&user_account=${user?.id + type}&user_password=${user?.password}"

        // 使用协程处理网络请求
        coroutineScope.launch {
            var success = false
            var retryCount = 0

            while (retryCount < MAX_RETRY_COUNT && !success) {
                success = try {
                    // 在IO线程执行网络请求
                    withContext(Dispatchers.IO) {
                        val call = getHttpClient().newCall(Request.Builder().url(url).build())
                        val response = call.execute()
                        response.body?.string()?.let { body ->
                            body.contains("认证成功") || body.contains("AC999")
                        } ?: false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "网络请求失败: ${e.message}")
                    false
                }

                if (!success) {
                    retryCount++
                    delay(1000) // 重试延迟1秒
                }
            }

            // 在主线程更新UI
            withContext(Dispatchers.Main) {
                if (success) {
                    this@PortalActivity.mCaptivePortal?.reportCaptivePortalDismissed()
                    Toast.makeText(this@PortalActivity, "认证成功", Toast.LENGTH_SHORT).show()
                } else {
                    this@PortalActivity.mCaptivePortal?.ignoreNetwork()
                    Toast.makeText(this@PortalActivity, "认证失败", Toast.LENGTH_SHORT).show()
                }
                val cm = getSystemService(ConnectivityManager::class.java)
                cm.bindProcessToNetwork(null) // 解除绑定
                SystemActionHelper.dismissDialog()
                finish()
            }
        }
    }


    private fun forwardToSystemComponent() {
        Log.i(TAG, "非目标网络，转发给系统原厂应用")

        val targetIntent = Intent("android.net.conn.CAPTIVE_PORTAL")
        if (intent.extras != null) {
            targetIntent.putExtras(intent.extras!!)
        }

        val pm = packageManager
        // 必须在 Manifest 添加 <queries> 才能查到
        val resolveInfos = pm.queryIntentActivities(targetIntent, PackageManager.MATCH_ALL)

        var foundSystem = false
        for (info in resolveInfos) {
            if (info.activityInfo.packageName != packageName) {
                targetIntent.component =
                    ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                targetIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    startActivity(targetIntent)
                    foundSystem = true
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 兜底：如果找不到系统组件，尝试用浏览器打开原始 Intent 里的 URL
        if (!foundSystem) {
            PortalManager.switchStatus(this, false)
            Toast.makeText(this, "处理失败，请手动登录", Toast.LENGTH_SHORT).show()
        }
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.bindProcessToNetwork(null) // 解除绑定
        SystemActionHelper.dismissDialog()
        finish()
    }
}