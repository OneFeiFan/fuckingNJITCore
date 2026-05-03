package com.feifan.fuckingnjit.utils.network

import android.content.Context
import android.webkit.CookieManager
import com.feifan.fuckingnjit.utils.network.HttpRequestHelper.Companion.COMMON_HEADERS
import com.feifan.fuckingnjit.utils.network.HttpRequestHelper.Companion.executeBaseRequest
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

/**
 * 基于 OkHttp + Jsoup 的 HTTP 请求工具，封装了教务系统 WebVPN 环境下的通用请求能力。
 *
 * 核心职责：
 * - 自动携带 WebView Cookie 并定期通过 Jsoup 检测登录会话有效性
 * - 支持表单 POST、JSON POST、GET 三种请求方式
 * - 统一异常处理，所有网络错误均抛出 [ApiException]
 * - 提供文件下载与 HTML 解析（Jsoup Document）两种响应读取方式
 *
 * 所有公开方法均为挂起函数，内部自动切换至 IO 调度器执行。
 */
class HttpRequestHelper {
    companion object {
        private var lastLoginCheckTime = 0L //控制登录状态检测
        private const val LOGIN_CHECK_INTERVAL = 60 * 1000 // 控制一分钟检测一次
        private val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true) // 连接失败重试
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

        /**
         * 下载指定 URL 的文件并写入应用内部存储。
         *
         * @param url      文件下载地址
         * @param fileName 保存到 filesDir 下的文件名
         * @param context  应用上下文，用于定位内部存储目录
         * @return 下载是否成功，失败时抛出 [ApiException]
         */
        suspend fun downloadFile(url: String, fileName: String, context: Context): Boolean =
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).headers(COMMON_HEADERS.toHeaders()).build()
                try {
                    val response = okHttpClient.newCall(request).execute()

                    // 拦截网络失败的情况，提前抛出异常
                    if (!response.isSuccessful) {
                        val status = NetworkStatusUtils.fromCode(response.code)
                        throw ApiException(status, "下载失败：HTTP状态码 ${response.code}")
                    }

                    // 拦截响应体为空的情况，安全地拿到非空 body
                    val body = response.body ?: throw ApiException(
                        NetworkStatus.ParseError,
                        "下载失败：响应体为空"
                    )

                    // 主干逻辑：安心处理正常的流写入
                    File(context.filesDir, fileName).outputStream().use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }

                    // 默认返回
                    true

                } catch (e: Exception) {
                    e.printStackTrace()
                    throw ApiException(
                        NetworkStatus.NetworkUnavailable,
                        "文件下载异常: ${e.message}",
                        e
                    )
                }
            }

        /** 将 CookieManager 返回的原始 cookie 字符串解析为键值对 map，供 Jsoup 使用 */
        private fun getPersistentCookies(cookie: String): Map<String, String> {
            return cookie.split(";")
                .associate { it.split("=").let { parts -> parts[0] to parts.getOrElse(1) { "" } } }
        }

        /**
         * 基于 OkHttp 的底层请求方法，支持 GET / POST（表单或 JSON）。
         *
         * 不包含登录态检测逻辑，仅负责构造请求、发送并返回响应文本。
         * 网络异常与 HTTP 错误状态码统一封装为 [ApiException] 抛出。
         *
         * @param url        请求地址
         * @param method     请求方式，默认 GET
         * @param formParams POST 表单参数，仅 POST 时生效
         * @param jsonStr    POST JSON 体，优先级高于 formParams
         * @param headers    自定义请求头，为 null 时使用 [COMMON_HEADERS]
         * @param cookie     手动指定的 Cookie 值，为 null 则不携带
         * @return 响应体文本
         */
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

        /**
         * 带登录态检测的请求入口，对外公开方法（getJsonResponse / getHtmlResponse）的最终实现。
         *
         * 执行逻辑：
         * 1. 从 CookieManager 读取 WebVPN cookie，为空则直接抛出未授权异常
         * 2. 距上次检查超过 60 秒时，通过 Jsoup 访问教务首页判断会话是否有效
         * 3. 会话有效时调用 [executeBaseRequest] 发起实际请求
         *
         * @param url         请求地址
         * @param method      请求方式
         * @param requestBody POST 表单参数
         * @return 响应文本
         */
        private suspend fun makeRequest(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): String {
            val cookie = cookieManager.getCookie(BASE_URL)
            if (cookie.isNullOrBlank()) {
                // 没有cookie就直接默认登录失效
                throw ApiException(NetworkStatus.Unauthorized, "Cookie 已失效，需要登录")
            }

            if ((System.currentTimeMillis() - lastLoginCheckTime) >= LOGIN_CHECK_INTERVAL) {
                lastLoginCheckTime = System.currentTimeMillis()
                try {
                    // 采用Jsoup访问教务系统首页，检测登录状态
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

        /**
         * 发起请求并返回原始 JSON 字符串。
         *
         * 内部自动完成登录态校验与 cookie 携带，适用于教务系统 API 接口调用。
         *
         * @param url         请求地址
         * @param method      请求方式，默认 GET
         * @param requestBody POST 表单参数
         * @return 服务端返回的 JSON 文本
         */
        suspend fun getJsonResponse(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): String = makeRequest(url, method, requestBody)

        /**
         * 发起请求并将响应文本解析为 Jsoup [Document] 对象。
         *
         * 适用于需要 DOM 选择器提取教务系统页面数据的场景。
         *
         * @param url         请求地址
         * @param method      请求方式，默认 GET
         * @param requestBody POST 表单参数
         * @return 解析后的 Jsoup Document
         */
        suspend fun getHtmlResponse(
            url: String,
            method: HttpMethod = HttpMethod.GET,
            requestBody: Map<String, String> = emptyMap()
        ): Document {
            return Jsoup.parse(makeRequest(url, method, requestBody))
        }
    }
}