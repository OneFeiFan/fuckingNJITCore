package com.feifan.fuckingnjit.utils.network

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.feifan.fuckingnjit.dao.FocusRecordDTO
import com.feifan.fuckingnjit.dao.FocusUploadRequest
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * 专注度记录数据同步管理器，负责将本地未上传的专注数据批量上报至服务端。
 *
 * 同步流程：从 ObjectBox 读取未上传记录 → 构造 DTO → 发起网络请求 → 成功后标记已上传。
 * 当前使用 mock 请求模拟网络行为，后续需替换为真实接口调用。
 */
@Suppress("unused")
object FocusDataSyncManager {

    private const val TAG = "FocusDataSyncManager"

    /**
     * 同步所有未上传的专注度记录到服务端。
     *
     * 该方法会在 IO 线程中执行完整的读取-转换-上传流程，
     * 上传成功后会将对应本地记录标记为已同步状态，失败则保留原始记录等待下次重试。
     *
     * @param context 应用上下文，用于获取设备匿名标识
     */
    suspend fun syncUnuploadedData(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 获取未上传的数据
            val unuploadedRecords = AppDataCenter.getUnuploadedFocusRecords()
            if (unuploadedRecords.isEmpty()) {
                Log.d(TAG, "没有需要同步的专注度数据")
                return@withContext
            }

            // 获取匿名设备哈希
            val deviceHash = getAnonymousDeviceHash(context)

            // 映射为 DTO
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

            // 如果服务端接收成功，更新本地状态
            if (isSuccess) {
                AppDataCenter.markFocusRecordsAsUploaded(unuploadedRecords)
                Log.i(TAG, "成功同步 ${unuploadedRecords.size} 条专注度数据到服务端")
            } else {
                Log.w(TAG, "数据同步失败，将在下次重试")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "数据同步过程发生异常", e)
        }
    }

    /**
     * 获取当前设备的匿名哈希标识。
     *
     * 基于 Android ID 加盐后做 SHA-256 摘要，同一设备每次调用结果一致，
     * 且不暴露原始硬件信息以保护隐私。
     */
    @SuppressLint("HardwareIds")
    private fun getAnonymousDeviceHash(context: Context): String {
        val androidId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "UNKNOWN_DEVICE"
        return hashString(androidId + "salt_for_privacy")
    }

    /** 对输入字符串做 SHA-256 摘要并返回十六进制小写结果 */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 模拟网络请求，延迟 1 秒后返回成功。接入真实接口后应删除此方法 */
    private suspend fun mockNetworkRequest(payload: FocusUploadRequest): Boolean {
        // 模拟网络耗时
        delay(1000)
        return true
    }
}