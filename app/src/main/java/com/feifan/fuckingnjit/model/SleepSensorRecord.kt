package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

//睡眠分数实体
@Entity
data class SleepSensorRecord(
    @Id var id: Long = 0,
    var timestamp: Long = 0,      // 时间戳
    var mixdata: Double = 0.0     // 综合得分
)