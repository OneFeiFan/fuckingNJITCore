package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany

/**
 * 每日数据聚合看板实体
 * 按天 (yyyy-MM-dd) 生成唯一记录
 */
@Entity
data class DailyRecord(
    @Id var id: Long = 0,

    @Index
    @Unique // 核心约束：保证每天只有唯一一条数据
    var dateStr: String = "",

    // --- 运动数据 (合并原 StepMonitorPrefs) ---
    var currentSteps: Int = 0,
    var lastRawSteps: Float = -1f,

    // --- 专注数据 (合并原 AppUsageStatsPrefs) ---
    var totalDistractionMins: Int = 0,

    // --- 睡眠数据 (合并原 SleepRecord 实体) ---
    var sleepStartTimeMs: Long = 0L,
    var wakeUpTimeMs: Long = 0L,
    var totalSleepMinutes: Int = 0
) {
    // --- 关联：当天的单节课专注度明细 (ObjectBox 一对多关系) ---
    @Backlink(to = "dailyRecord")
    lateinit var focusRecords: ToMany<ClassFocusRecord>
}