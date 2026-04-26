package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * 单节课专注度记录实体
 * 用于记录并增量累加每节课的摸鱼（违规）时长
 */
@Entity
data class ClassFocusRecord(
    @Id var id: Long = 0,

    // 冗余一个字符串日期（如 "2026-04-26"），极大提升按天出报告时的查询效率
    var dateStr: String = "",

    // 课程基础信息
    var courseName: String = "",
    var startTime: Long = 0L,
    var endTime: Long = 0L,

    // 核心增量字段：累计违规/摸鱼时长（单位建议统一为毫秒，方便后续计算和格式化）
    var distractionDurationMills: Long = 0L
)