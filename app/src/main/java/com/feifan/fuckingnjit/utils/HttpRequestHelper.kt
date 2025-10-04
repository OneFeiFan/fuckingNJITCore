package com.feifan.fuckingnjit.utils

import android.content.Context
import android.webkit.CookieManager
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class HttpRequestHelper {

    constructor(cookieManager: CookieManager) {
        HttpRequestHelper.cookieManager = cookieManager
    }

    private var lastLoginCheckTime = 0L
    private val LOGIN_CHECK_INTERVAL = 5 * 60 * 1000 // 30分钟检查一次

    companion object {
        private val okHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
        private lateinit var cookieManager: CookieManager
        const val BASE_URL = "https://casb.njit.edu.cn"
        const val WEBVPN_PATH =
            "/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a"
        val COMMON_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Connection" to "keep-alive"
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
    }

    private fun getPersistentCookies(cookie: String): Map<String, String> {
        return cookie.split(";")
            .associate { it.split("=").let { parts -> parts[0] to parts.getOrElse(1) { "" } } }
    }

    private fun checkLoginIfNeeded(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastLoginCheckTime >= LOGIN_CHECK_INTERVAL // 在有效期内，跳过检查
    }

    private suspend fun makeRequest(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        additionalHeaders: Map<String, String> = emptyMap(),
        requestBody: Map<String, String> = emptyMap()
    ): String {
        val cookie = cookieManager.getCookie(BASE_URL)
        if (cookie.isNullOrBlank()) {
            Manager.showToast("需要登录")
            Manager.startLogin(true)
            return ""
        }

        if (checkLoginIfNeeded()) {
            try {
                // 创建一个Connection对象
                val connection: Connection =
                    Jsoup.connect("https://casb.njit.edu.cn/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a/jwglxt/xtgl/index_initMenu.html")
                // 设置Cookie
                connection.cookies(getPersistentCookies(cookie))
                // 执行请求并获取Document对象
                val html: Document = connection.get()
                if (html.text().contains("登录页面")) {
                    Manager.showToast("需要登录")
                    Manager.startLogin(true)
                    return ""
                }
            } catch (e: IOException) {
                when {
                    // 网络异常
                    e is ConnectException || e is UnknownHostException || e is SocketException -> {
                        Manager.handleException(e, "网络异常，请检查网络连接")
                    }

                    e.message?.contains("Too many redirects") == true -> {
                        Manager.showToast("需要登录")
                        Manager.startLogin(true)
                    }

                    e is SocketTimeoutException -> {
                        Manager.handleException(e, "请求超时，请稍后再试")
                    }
                    // 其他异常
                    else -> {
                        Manager.handleException(e, "登录验证失败")
                    }
                }
                return ""
            }
        }

        lastLoginCheckTime = System.currentTimeMillis()
        var formBody: RequestBody = FormBody.Builder().build()
        if (method == HttpMethod.POST) {
            val builder = FormBody.Builder()
            // 遍历 Map，将键值对添加到 FormBody
            for ((key, value) in requestBody) {
                builder.add(key, value)
            }
            formBody = builder.build()
        }
        val builder = Request.Builder().url(url)
            .headers(COMMON_HEADERS.toHeaders())
            .headers(additionalHeaders.toHeaders())
            .addHeader("Cookie", cookie)
        if (method == HttpMethod.POST) {
            builder.method("POST", formBody)
        }
        val request = builder.build()
        return suspendCoroutine { continuation ->
            try {
                val response = okHttpClient.newCall(request).execute()
                continuation.resume(response.body?.string() ?: "")
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
        additionalHeaders: Map<String, String> = emptyMap(),
        requestBody: Map<String, String> = emptyMap()
    ): String {
        val result = makeRequest(url, method, additionalHeaders, requestBody)
        return result
    }

    suspend fun getHtmlResponse(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        additionalHeaders: Map<String, String> = emptyMap(),
        requestBody: Map<String, String> = emptyMap()
    ): Document {

        val result = makeRequest(url, method, additionalHeaders, requestBody)
        if (result.isEmpty()) {
            return Document.createShell("")
        } else {
            return Jsoup.parse(result)
        }
    }
}