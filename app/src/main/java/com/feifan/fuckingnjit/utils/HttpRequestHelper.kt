package com.feifan.fuckingnjit.utils

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.util.concurrent.TimeUnit

class HttpRequestHelper {
    companion object {
        private var lastLoginCheckTime = 0L
        private const val LOGIN_CHECK_INTERVAL = 60 * 1000
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

        suspend fun downloadFile(url: String, fileName: String, context: Context): Boolean =
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).headers(COMMON_HEADERS.toHeaders()).build()
                try {
                    val response = okHttpClient.newCall(request).execute()

                    // 1. 卫语句：拦截网络失败的情况，提前抛出异常
                    if (!response.isSuccessful) {
                        val status = NetworkStatusUtils.fromCode(response.code)
                        throw ApiException(status, "下载失败：HTTP状态码 ${response.code}")
                    }

                    // 2. 卫语句：拦截响应体为空的情况，安全地拿到非空 body
                    val body = response.body ?: throw ApiException(
                        NetworkStatus.ParseError,
                        "下载失败：响应体为空"
                    )

                    // 3. 主干逻辑：安心处理正常的流写入
                    File(context.filesDir, fileName).outputStream().use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }

                    // 4. Lambda 的最后一行自动作为整个 withContext 的返回值，直接写 true 即可
                    true

                } catch (e: ApiException) {
                    throw e
                } catch (e: Exception) {
                    throw ApiException(
                        NetworkStatus.NetworkUnavailable,
                        "文件下载异常: ${e.message}",
                        e
                    )
                }
            }

        private fun getPersistentCookies(cookie: String): Map<String, String> {
            return cookie.split(";")
                .associate { it.split("=").let { parts -> parts[0] to parts.getOrElse(1) { "" } } }
        }

        private fun checkLoginIfNeeded(): Boolean =
            (System.currentTimeMillis() - lastLoginCheckTime) >= LOGIN_CHECK_INTERVAL

        suspend fun executeBaseRequest(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            formParams: Map<String, String>? = null,
            jsonStr: String? = null,
            headers: Headers? = null,
            cookie: String? = null
        ): String = withContext(Dispatchers.IO) {

            val requestBuilder = Request.Builder().url(url)
            requestBuilder.headers(headers ?: COMMON_HEADERS.toHeaders())
            if (!cookie.isNullOrBlank()) requestBuilder.addHeader("Cookie", cookie)

            if (method == HttpMethod.POST) {
                val body = when {
                    jsonStr != null -> jsonStr.toRequestBody("application/json; charset=utf-8".toMediaType())
                    formParams != null -> FormBody.Builder()
                        .apply { formParams.forEach { (k, v) -> add(k, v) } }.build()

                    else -> FormBody.Builder().build()
                }
                requestBuilder.post(body)
            } else {
                requestBuilder.get()
            }

            try {
                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        // 🎯 核心运用：将 HTTP 错误码完美映射到你的密封类
                        throw ApiException(
                            NetworkStatusUtils.fromCode(response.code),
                            "请求失败，状态码: ${response.code}"
                        )
                    }
                    response.body?.string() ?: throw ApiException(
                        NetworkStatus.ParseError,
                        "响应体为空"
                    )
                }
            } catch (e: IOException) {
                throw ApiException(
                    NetworkStatus.NetworkUnavailable,
                    "网络请求失败: ${e.message}",
                    e
                )
            }
        }

        private suspend fun makeRequest(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): String {
            val cookie = cookieManager.getCookie(BASE_URL)
            if (cookie.isNullOrBlank()) {
                // 🎯 核心运用：直接抛出 Unauthorized (401)
                throw ApiException(NetworkStatus.Unauthorized, "Cookie 已失效，需要登录")
            }

            if (checkLoginIfNeeded()) {
                lastLoginCheckTime = System.currentTimeMillis()
                try {
                    val connection =
                        Jsoup.connect("$BASE_URL$WEBVPN_PATH/jwglxt/xtgl/index_initMenu.html")
                            .cookies(getPersistentCookies(cookie)).followRedirects(false)
                            .timeout(10000)
                    val response = connection.execute()

                    when (response.statusCode()) {
                        HttpURLConnection.HTTP_OK -> {}
                        HttpURLConnection.HTTP_MOVED_TEMP -> {
                            val location = response.header("Location")
                            if (location?.contains("index_initMenu.html") == true || location?.contains(
                                    "login_slogin"
                                ) == true
                            ) {
                                throw ApiException(NetworkStatus.Unauthorized, "会话已过期 (302)")
                            }
                        }

                        else -> throw ApiException(
                            NetworkStatusUtils.fromCode(response.statusCode()),
                            "会话验证失败"
                        )
                    }
                } catch (e: IOException) {
                    throw ApiException(
                        NetworkStatus.GatewayTimeout,
                        "验证会话超时: ${e.message}",
                        e
                    )
                }
            }

            try {
                return executeBaseRequest(url, method, requestBody, cookie = cookie)
            } catch (e: Exception) {
                if (e is ProtocolException && e.message?.contains("Too many follow-up requests") == true) {
                    throw ApiException(NetworkStatus.Forbidden, "重定向次数过多")
                }
                throw e
            }
        }

        suspend fun getJsonResponse(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): String = makeRequest(url, method, requestBody)

        suspend fun getHtmlResponse(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): Document {
            return Jsoup.parse(makeRequest(url, method, requestBody))
        }
    }
}