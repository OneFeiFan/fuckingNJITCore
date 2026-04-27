package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * 单节课专注度记录实体
 * 用于记录并增量累加每节课的摸鱼（违规）时长
 */
@Entity
data class ClassFocusRecord(
    @Id var id: Long = 0,

    @Index // 加上索引，提升按天查询效率
    var dateStr: String = "",

    // --- 新增：核心关联字段 ---
    @Index // 加上索引，方便后续可能按课程查询
    var courseId: String = "",

    // 课程基础信息 (courseName 降级为 UI 展示用的冗余字段)
    var courseName: String = "",
    var startTime: Long = 0L,
    var endTime: Long = 0L,

    // 核心增量字段：累计违规/摸鱼时长（毫秒）
    var distractionDurationMills: Long = 0L,

    // --- 新增：数据同步状态标记 ---
    var isUploaded: Boolean = false
)