package com.feifan.fuckingnjit.service

import SleepUploadPayload
import UploadSensorPoint
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.SleepRecord
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.utils.HttpMethod
import com.feifan.fuckingnjit.utils.HttpRequestHelper
import com.feifan.fuckingnjit.utils.TimeManager
import com.feifan.fuckingnjit.utils.database.SleepRecordBoxUtils
import com.feifan.fuckingnjit.utils.database.SleepSensorBoxUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import java.time.LocalDate

object DataSyncService {

    suspend fun uploadAndClearData() = withContext(Dispatchers.IO) {
        try {
            val userId = UserManagerImpl.getInstance().getCurrentUser().id
            val deviceModel = android.os.Build.MODEL.replace(" ", "_")

            val window = TimeManager.getInstance().getTargetSleepWindow()
            val startTimeMs = window.first
            val endTimeMs = window.second

            // 回收机制
            SleepSensorBoxUtils.deleteRecordsBefore(startTimeMs)

            val uploadRecords = SleepSensorBoxUtils.getRecordsBetween(startTimeMs, endTimeMs)
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
                        val existingRecord = SleepRecordBoxUtils.getByDate(targetDate)

                        if (existingRecord != null) {
                            existingRecord.totalSleepMinutes = totalMinutes
                            existingRecord.sleepStartTimeMs = sleepStartTimeMs
                            existingRecord.wakeUpTimeMs = wakeUpTimeMs
                            SleepRecordBoxUtils.insertOrUpdate(existingRecord)
                        } else {
                            val newRecord = SleepRecord(
                                targetDate = targetDate,
                                totalSleepMinutes = totalMinutes,
                                sleepStartTimeMs = sleepStartTimeMs,
                                wakeUpTimeMs = wakeUpTimeMs
                            )
                            SleepRecordBoxUtils.insertOrUpdate(newRecord)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("⚠️ 数据同步中断: ${e.message}。")
        }
    }
}