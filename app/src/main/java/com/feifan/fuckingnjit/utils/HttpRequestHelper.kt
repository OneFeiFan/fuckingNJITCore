package com.feifan.fuckingnjit.utils

import android.content.Context
import android.webkit.CookieManager
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class HttpRequestHelper {
    companion object {
        private var lastLoginCheckTime = 0L
        private const val LOGIN_CHECK_INTERVAL = 60 * 1000 // 1分钟检查一次
        private val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
        private val cookieManager: CookieManager = CookieManager.getInstance()
        const val BASE_URL = "https://casb.njit.edu.cn"
        const val WEBVPN_PATH =
            "/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a"
        val COMMON_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Referer" to "${BASE_URL}/jwglxt/xtgl/login_slogin.html",
            "Origin" to BASE_URL
        )

        suspend fun downloadFile(
            url: String,
            fileName: String,
            context: Context
        ): Boolean {
            val request = Request.Builder()
                .url(url)
                .headers(COMMON_HEADERS.toHeaders())
                .build()

            return suspendCoroutine { continuation ->
                try {
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            val file = File(context.filesDir, fileName)
                            file.outputStream().use { output ->
                                body.byteStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            continuation.resume(true)
                        } ?: continuation.resume(false)
                    } else {
                        continuation.resume(false)
                    }
                } catch (e: Exception) {
                    Manager.handleException(e, "文件下载失败")
                    continuation.resume(false)
                }
            }
        }


        private fun getPersistentCookies(cookie: String): Map<String, String> {
            return cookie.split(";")
                .associate { it.split("=").let { parts -> parts[0] to parts.getOrElse(1) { "" } } }
        }

        private fun checkLoginIfNeeded(): Boolean {
            val currentTime = System.currentTimeMillis()
            return currentTime - lastLoginCheckTime >= LOGIN_CHECK_INTERVAL // 在有效期内，跳过检查
        }

        // 网络异常统一处理
        private fun handleNetworkException(e: IOException) {
            when (e) {
                is ConnectException, is UnknownHostException, is SocketException -> {
                    Manager.handleException(e, "网络异常，请检查网络连接")
                }

                else -> {
                    Manager.handleException(e, "登录验证失败")
                }
            }
        }

        private suspend fun makeRequest(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): String {
            val cookie = cookieManager.getCookie(BASE_URL)
            if (cookie.isNullOrBlank()) {
                Manager.showToast("需要登录")
                Manager.startLogin(true)
                return ""
            }

            if (checkLoginIfNeeded()) {
                lastLoginCheckTime = System.currentTimeMillis()
                try {
                    val connection = Jsoup.connect("$BASE_URL$WEBVPN_PATH/jwglxt/xtgl/index_initMenu.html")
                        .cookies(getPersistentCookies(cookie))
                        .followRedirects(false)
                        .timeout(10000) // 10秒超时

                    val response = connection.execute()

                    when (response.statusCode()) {
                        HttpURLConnection.HTTP_OK -> {}
                        //直接重定向到登录页 -> 会话失效
                        HttpURLConnection.HTTP_MOVED_TEMP -> {
                            val location = response.header("Location")
                            if (location?.contains("index_initMenu.html") == true || location?.contains("login_slogin") == true) {
                                Manager.showToast("需要登录")
                                Manager.startLogin(true)
                                return ""
                            }
                        }
                        // 其他状态码处理
                        else -> {
                            Manager.handleException(Exception("HTTP状态异常: ${response.statusCode()}"), "登录验证失败")
                            return ""
                        }
                    }
                } catch (e: IOException) {
                    when (e) {
                        is SocketTimeoutException -> {
                            Manager.handleException(e, "请求超时，请稍后再试")
                        }else -> {
                            handleNetworkException(e) // 统一处理网络异常
                            return ""
                        }
                    }
                }
            }
            val request = Request.Builder()
                .url(url)
                .headers(COMMON_HEADERS.toHeaders())
                .addHeader("Cookie", cookie)
                .apply {
                    if (method == HttpMethod.POST) {
                        val formBody = FormBody.Builder().apply {
                            requestBody.forEach { (key, value) -> add(key, value) }
                        }.build()
                        method("POST", formBody)
                    }
                }
                .build()
            return suspendCoroutine { continuation ->
                try {
                    val response = okHttpClient.newCall(request).execute()
                    val result = response.body?.string() ?: ""
                    response.close()
                    continuation.resume(result)
                } catch (e: Exception) {
                    if (e is ProtocolException) {
                        if (e.message?.contains("Too many follow-up requests") == true) {
                            Manager.handleException(e, "重试次数过多，cookie可能失效,请重新登录")
                            Manager.startLogin(true)
                        } else {
                            Manager.handleException(e, "捕获到其他IO异常")
                        }
                    } else {
                        if (e.message?.contains("onnect") == true) {
                            Manager.handleException(e, "网络异常，请检查网络连接")
                        }
                        Manager.handleException(e, "捕获到非ProtocolException异常")
                    }
                    continuation.resume("")
                }
            }
        }
        suspend fun getJsonResponse(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): String {
            return makeRequest(url, method, requestBody)
        }

        suspend fun getHtmlResponse(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): Document {
            val result = makeRequest(url, method, requestBody)
            if (result.isEmpty()) {
                return Document.createShell("")
            } else {
                return Jsoup.parse(result)
            }
        }
    }
}