package com.feifan.fuckingnjit.utils.network

import com.alibaba.fastjson.JSONObject

/** 网络请求异常封装类 */
class ApiException(
    val status: NetworkStatus,
    override val message: String = status.message,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 网络状态码密封类
 *
 * 覆盖标准 HTTP 状态码及自定义业务错误码，
 * 提供 toJsonResult 方法用于快速构建统一响应格式。
 */
@Suppress("unused")
sealed class NetworkStatus(
    val code: Int,
    val message: String
) {
    // 成功状态 (2xx)
    object Success : NetworkStatus(200, "请求成功")
    object Created : NetworkStatus(201, "资源创建成功")
    object Accepted : NetworkStatus(202, "请求已接受")

    // 客户端错误 (4xx)
    object BadRequest : NetworkStatus(400, "无效请求")
    object Unauthorized : NetworkStatus(401, "未授权")
    object Forbidden : NetworkStatus(403, "禁止访问")
    object NotFound : NetworkStatus(404, "资源未找到")
    object RequestTimeout : NetworkStatus(408, "请求超时")

    // 服务端错误 (5xx)
    object InternalError : NetworkStatus(500, "服务器内部错误")
    object ServiceUnavailable : NetworkStatus(503, "服务不可用")
    object GatewayTimeout : NetworkStatus(504, "网关超时")

    // 自定义状态码 (6xx)
    object NetworkUnavailable : NetworkStatus(600, "网络不可用")
    object ParseError : NetworkStatus(601, "数据解析失败")
    object UnknownError : NetworkStatus(699, "未知错误")

    // 扩展函数：检查状态码范围
    fun isSuccess(): Boolean = code in 200..299
    fun isClientError(): Boolean = code in 400..499
    fun isServerError(): Boolean = code in 500..599
    fun isCustomError(): Boolean = code >= 600

    // 重写toString
    override fun toString(): String = "$code: $message"
    fun toJsonResult(data: Any? = null): JSONObject {
        return JSONObject().apply {
            put("code", code)
            put("message", message)
            data?.let { put("data", it) }
        }
    }
}

/**
 * HTTP / 自定义状态码与 [NetworkStatus] 枚举的互转工具。
 */
object NetworkStatusUtils {
    /**
     * 将数值型 HTTP 或自定义状态码映射为对应的 [NetworkStatus] 枚举实例。
     *
     * @param code 原始状态码（200/404/500/600 等）
     * @return 匹配的枚举值，无匹配时返回 [NetworkStatus.UnknownError]
     */
    fun fromCode(code: Int): NetworkStatus {
        return when (code) {
            200 -> NetworkStatus.Success
            201 -> NetworkStatus.Created
            202 -> NetworkStatus.Accepted
            400 -> NetworkStatus.BadRequest
            401 -> NetworkStatus.Unauthorized
            403 -> NetworkStatus.Forbidden
            404 -> NetworkStatus.NotFound
            408 -> NetworkStatus.RequestTimeout
            500 -> NetworkStatus.InternalError
            503 -> NetworkStatus.ServiceUnavailable
            504 -> NetworkStatus.GatewayTimeout
            600 -> NetworkStatus.NetworkUnavailable
            601 -> NetworkStatus.ParseError
            else -> NetworkStatus.UnknownError
        }
    }
}