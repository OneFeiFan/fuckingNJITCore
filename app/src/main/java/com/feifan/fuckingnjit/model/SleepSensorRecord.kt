package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * 睡眠传感器数据点实体
 *
 * 记录单个时间点的睡眠综合得分，用于上报至服务端进行睡眠质量分析。
 *
 * @param timestamp 数据采集时间戳（毫秒）
 * @param mixdata 睡眠综合得分
 */
@Entity
data class SleepSensorRecord(
    @Id var id: Long = 0,
    var timestamp: Long = 0,
    var mixdata: Double = 0.0
)