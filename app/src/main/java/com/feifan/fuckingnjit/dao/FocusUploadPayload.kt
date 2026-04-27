package com.feifan.fuckingnjit.dao

import com.alibaba.fastjson.annotation.JSONField

/**
 * 专注度数据上报 DTO
 */
data class FocusUploadRequest(
    @JSONField(name = "device_hash") val deviceHash: String,
    @JSONField(name = "records") val records: List<FocusRecordDTO>
)

data class FocusRecordDTO(
    @JSONField(name = "course_id") val courseId: String,
    @JSONField(name = "course_name") val courseName: String,
    @JSONField(name = "total_duration_mills") val totalDurationMills: Long, // 该节课理论总时长
    @JSONField(name = "distraction_duration_mills") val distractionDurationMills: Long, // 摸鱼时长
    @JSONField(name = "record_date") val recordDate: String // 发生日期，方便服务端分区或定期清理
)