package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToOne

/**
 * 单节课专注度记录实体
 *
 * 记录每节课的起止时间和分心时长，通过 [dailyRecord] 关联到所属的每日汇总记录。
 */
@Entity
data class ClassFocusRecord(
    @Id var id: Long = 0,

    @Index // 按课程 ID 索引，方便按课程历史统计
    var courseId: String = "",
    var courseName: String = "",
    var startTime: Long = 0L,
    var endTime: Long = 0L,
    var distractionDurationMills: Long = 0L,
    var isUploaded: Boolean = false
) {
    /** 指向上级 DailyRecord 的关联关系 */
    lateinit var dailyRecord: ToOne<DailyRecord>
}