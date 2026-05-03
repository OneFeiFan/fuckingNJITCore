package com.feifan.fuckingnjit.service

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.dao.SleepUploadPayload
import com.feifan.fuckingnjit.dao.UploadSensorPoint
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.fuckingnjit.utils.network.HttpMethod
import com.feifan.fuckingnjit.utils.network.HttpRequestHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import java.time.LocalDate

/**
 * 数据同步服务
 *
 * 负责将本地缓冲的睡眠传感器数据上传至服务端，
 * 并在成功响应后回写睡眠分析结果（时长、入睡/起床时间）到本地数据库。
 */
object DataSyncService {

    /**
     * 执行数据上传与清理流程
     *
     * 从本地数据库取出目标时间窗口内的传感器记录，组装上传载荷后发送至服务端。
     * 若服务端返回有效的睡眠分析结果则持久化到今日记录中。
     */
    suspend fun uploadAndClearData() = withContext(Dispatchers.IO) {
        try {
            val userId = UserManagerImpl.getInstance().getCurrentUser().id
            val deviceModel = android.os.Build.MODEL.replace(" ", "_")

            val window = Tools.getTargetSleepWindow()
            val startTimeMs = window.first
            val endTimeMs = window.second

            // 回收机制
            AppDataCenter.clearOldSensorsBefore(startTimeMs)

            val uploadRecords = AppDataCenter.getSensorRecordsBetween(startTimeMs, endTimeMs)
            if (uploadRecords.isEmpty()) return@withContext

            val uploadPoints = uploadRecords.map { UploadSensorPoint.fromLocalRecord(it) }
            val payload = SleepUploadPayload(userId = userId + deviceModel, data = uploadPoints)
            val requestBody = JSONObject.toJSONString(payload)

            val customHeaders = Headers.Builder().apply {
                add("ngrok-skip-browser-warning", "true")
            }.build()

            val responseString = HttpRequestHelper.executeBaseRequest(
                url = "https://unplacid-davian-unatoned.ngrok-free.dev/api/sleep/upload",
                method = HttpMethod.POST,
                jsonStr = requestBody,
                headers = customHeaders
            )

            val responseObj = JSON.parseObject(responseString)

            if (responseObj != null && responseObj.getInteger("code") == 200) {
                val dataObj = responseObj.getJSONObject("data")
                if (dataObj != null && dataObj.isNotEmpty()) {
                    val targetDate = LocalDate.now().toString()
                    val sleepHours = dataObj.getDoubleValue("sleepHours")
                    val sleepStartTimeMs = dataObj.getLongValue("sleepStartTimeMs")
                    val wakeUpTimeMs = dataObj.getLongValue("wakeUpTimeMs")

                    if (sleepHours > 0) {
                        val totalMinutes = (sleepHours * 60).toInt()

                        AppDataCenter.saveSleepResult(
                            dateStr = targetDate,
                            sleepStartMs = sleepStartTimeMs,
                            wakeUpMs = wakeUpTimeMs,
                            durationMins = totalMinutes
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("⚠️ 数据同步中断: ${e.message}。")
        }
    }
}