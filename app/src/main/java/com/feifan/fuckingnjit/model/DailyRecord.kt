package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany

// 每日健康数据实体
@Entity
data class DailyRecord(
    @Id var id: Long = 0,

    @Index
    @Unique // 保证每天只有唯一一条数据
    var dateStr: String = "",//日期
    var currentSteps: Int = 0,//实际步数
    var lastRawSteps: Float = -1f,//计步器原始数据，用于和计步器数据比较校准误差
    var totalDistractionMins: Int = 0,//走神时长
    var sleepStartTimeMs: Long = 0L,//入睡时间
    var wakeUpTimeMs: Long = 0L,//醒来时间
    var totalSleepMinutes: Int = 0//睡眠时长
) {
    @Backlink(to = "dailyRecord")
    lateinit var focusRecords: ToMany<ClassFocusRecord>//关联多个当天的单节课专注度
}