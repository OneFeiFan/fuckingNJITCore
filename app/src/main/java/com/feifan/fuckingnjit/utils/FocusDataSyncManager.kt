package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.feifan.fuckingnjit.dao.FocusRecordDTO
import com.feifan.fuckingnjit.dao.FocusUploadRequest
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object FocusDataSyncManager {

    private const val TAG = "FocusDataSyncManager"

    /**
     * 触发数据同步（建议在 App 启动、连接 WiFi 或每天定时任务时调用）
     */
    suspend fun syncUnuploadedData(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 1. 获取未上传的数据
            val unuploadedRecords = AppDataCenter.getUnuploadedFocusRecords()
            if (unuploadedRecords.isEmpty()) {
                Log.d(TAG, "没有需要同步的专注度数据")
                return@withContext
            }

            // 2. 获取匿名设备哈希
            val deviceHash = getAnonymousDeviceHash(context)

            // 3. 映射为 DTO
            val dtoList = unuploadedRecords.map { record ->
                FocusRecordDTO(
                    courseId = record.courseId,
                    courseName = record.courseName,
                    totalDurationMills = record.endTime - record.startTime,
                    distractionDurationMills = record.distractionDurationMills,
                    recordDate = record.dailyRecord.target?.dateStr ?: ""
                )
            }

            for (dTO in dtoList) {
                Log.i(TAG, dTO.courseId + dTO.courseName)
            }

            val payload = FocusUploadRequest(deviceHash, dtoList)

            // 4. 发送网络请求 (此处伪代码，请替换为你项目中实际的 Retrofit 或 Http 请求方法)
            // val isSuccess = WebService.uploadFocusRecords(payload)
            val isSuccess = mockNetworkRequest(payload) // TODO: 接入真实的 POST 请求

            // 5. 如果服务端接收成功，更新本地状态
            if (isSuccess) {
                AppDataCenter.markFocusRecordsAsUploaded(unuploadedRecords)
                Log.i(TAG, "成功同步 ${unuploadedRecords.size} 条专注度数据到服务端")
            } else {
                Log.w(TAG, "数据同步失败，将在下次重试")
            }

        } catch (e: Exception) {
            Log.e(TAG, "数据同步过程发生异常", e)
        }
    }

    /**
     * 获取设备匿名哈希值 (保证同一个设备每次获取都一致，但不暴露真实硬件信息)
     */
    @SuppressLint("HardwareIds")
    private fun getAnonymousDeviceHash(context: Context): String {
        val androidId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "UNKNOWN_DEVICE"
        return hashString(androidId + "salt_for_privacy")
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun mockNetworkRequest(payload: FocusUploadRequest): Boolean {
        // 模拟网络耗时
        kotlinx.coroutines.delay(1000)
        return true
    }
}