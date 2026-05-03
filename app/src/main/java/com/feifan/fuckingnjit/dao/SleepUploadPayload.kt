package com.feifan.fuckingnjit.dao

import com.feifan.fuckingnjit.model.SleepSensorRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 睡眠传感器数据点，对应服务端 data 数组中的单个元素
 */
data class UploadSensorPoint(
    val time: String,
    val value: Double
) {
    companion object {
        /**
         * 将本地 ObjectBox 实体转换为上传用的数据点对象
         *
         * @param record 本地睡眠传感器记录实体
         * @return 格式化后的上传数据点
         */
        fun fromLocalRecord(record: SleepSensorRecord): UploadSensorPoint {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            // 将毫秒时间戳转换为带时区的时间字符串
            val formattedTime = Instant.ofEpochMilli(record.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
            return UploadSensorPoint(
                time = formattedTime,
                value = record.mixdata
            )
        }
    }
}

/**
 * 睡眠数据完整上传载荷
 *
 * @param userId 用户标识，用于服务端区分数据归属
 * @param data 传感器数据点列表
 */
data class SleepUploadPayload(
    val userId: String,
    val data: List<UploadSensorPoint>
)