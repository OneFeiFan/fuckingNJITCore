package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToMany

/**
 * 每日健康数据汇总实体
 *
 * 以天为粒度记录用户的步数、分心时长和睡眠等核心健康指标，
 * 通过 [dateStr] 字段保证每天只有唯一一条记录。关联当天的所有 [ClassFocusRecord]。
 */
@Entity
data class DailyRecord(
    @Id var id: Long = 0,

    @Index
    @Unique
    var dateStr: String = "",
    var currentSteps: Int = 0,
    var lastRawSteps: Float = -1f,
    var totalDistractionMins: Int = 0,
    var sleepStartTimeMs: Long = 0L,
    var wakeUpTimeMs: Long = 0L,
    var totalSleepMinutes: Int = 0
) {
    /** 关联的当天单节课专注度记录列表 */
    @Backlink(to = "dailyRecord")
    lateinit var focusRecords: ToMany<ClassFocusRecord>
}