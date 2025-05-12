package com.feifan.fuckingnjit.utils

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.coroutines.suspendCoroutine


class HttpRequestHelper(
    private val okHttpClient: OkHttpClient,
    private val cookieManager: CookieManager
) {
    companion object {
        const val BASE_URL = "https://casb.njit.edu.cn"
        const val WEBVPN_PATH =
            "/http/webvpn3e1a11b7208e283ab07ade5d2913fc13d6f6fe09d2dc7372db2a51a14aa4167a"
        val COMMON_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
            "Accept" to "*/*",
            "Connection" to "keep-alive"
        )
    }



    private suspend fun makeRequest(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        additionalHeaders: Map<String, String> = emptyMap(),
        requestBody: Map<String, String> = emptyMap()
    ): String {
        val cookie = cookieManager.getCookie(BASE_URL)
        if (cookie.isNullOrBlank()) {
//            Manager.showToast("cookie为空，需要登录")
            Manager.startLogin()
            throw Exception("需要登录")
        }
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
//        Handler(Looper.getMainLooper()).post {
            Manager.showToast("请求地址: $url")
//        }
        return suspendCoroutine { continuation ->
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
//                    Handler(Looper.getMainLooper()).post {
                        Manager.showToast("请求成功: ${response.code}")
//                    }
//                    okHttpClient.dispatcher.executorService.shutdown()
                    if (!response.isSuccessful) {
                        continuation.resumeWith(Result.failure(IOException("请求失败: ${response.code}")))
                        return
                    }
                    continuation.resumeWith(Result.success(response.body?.string() ?: ""))
                }

                override fun onFailure(call: Call, e: IOException) {
//                    Handler(Looper.getMainLooper()).post {
                        Manager.showToast("请求失败: ${e.message}")
//                    }
//                    okHttpClient.dispatcher.executorService.shutdown()
                    continuation.resumeWith(Result.failure(e))
                    if (e is java.net.ProtocolException) {
                        if (e.message?.contains("Too many follow-up requests") == true) {
                            println("捕获到Too many follow-up requests异常: ${e.message}")
//                            Handler(Looper.getMainLooper()).post {
                                Manager.showToast("重试次数过多，cookie可能失效,请重新登录")
//                            }
                            Manager.startLogin(true)
                        } else {
                            println("捕获到其他IO异常: ${e.message}")

                        }
                    } else {
                        println("捕获到非ProtocolException异常: ${e.message}")
//                        Handler(Looper.getMainLooper()).post {
                            Manager.showToast("捕获到非ProtocolException异常: ${e.message}")
//                        }
                    }
                }
            })
        }
    }


    suspend fun getJsonResponse(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        additionalHeaders: Map<String, String> = emptyMap(),
        requestBody: Map<String, String> = emptyMap()
    ): String {
        return try {
            makeRequest(url, method,additionalHeaders,requestBody)
        } catch (e: Exception) {
            """{"state":"error","message":"${e.message}"}"""
        }
    }

    suspend fun getHtmlResponse(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        additionalHeaders: Map<String, String> = emptyMap(),
        requestBody: Map<String, String> = emptyMap()
    ): Document {
        return try {
            Jsoup.parse(makeRequest(url,method, additionalHeaders,requestBody))
        } catch (e: Exception) {
            println("获取HTML失败: ${e.message}")
//            Handler(Looper.getMainLooper()).post {
                Manager.showToast("获取HTML失败: ${e.message}")
//            }

            Jsoup.parse("""<html><body><h1>获取HTML失败: ${e.message}</h1></body></html>""")
        }
    }
}