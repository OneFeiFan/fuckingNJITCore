package com.feifan.fuckingnjit.utils.database

import com.feifan.fuckingnjit.model.ClassFocusRecord
import com.feifan.fuckingnjit.model.ClassFocusRecord_
import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ClassFocusRecordBoxUtils : BaseBoxUtils() {

    private val box: Box<ClassFocusRecord> by lazy {
        getBox(ClassFocusRecord::class.java)
    }
    override fun getDatabaseName() = "SLEEP_RECORD_STATISTICS"

    /**
     * 增量记录摸鱼时长（核心逻辑）
     * @param courseName 当前课程名称
     * @param courseStartTime 课程开始时间戳
     * @param courseEndTime 课程结束时间戳
     * @param addedDistractionMills 本次新增的违规时长（毫秒）
     */
    fun addDistractionTime(
        courseName: String,
        courseStartTime: Long,
        courseEndTime: Long,
        addedDistractionMills: Long
    ) {
        if (addedDistractionMills <= 0) return

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 1. 尝试查找当前这节课的记录（根据日期、课名和时间精准定位）
        val existingRecord = box.query {
            // 【修改】：给字符串类型的查询加上 StringOrder.CASE_SENSITIVE
            equal(ClassFocusRecord_.dateStr, todayStr, QueryBuilder.StringOrder.CASE_SENSITIVE)
            equal(ClassFocusRecord_.courseName, courseName, QueryBuilder.StringOrder.CASE_SENSITIVE)
            // Long 类型的查询不需要加
            equal(ClassFocusRecord_.startTime, courseStartTime)
        }.findFirst()

        // 2. 增量更新或新建记录
        val targetRecord = existingRecord ?: ClassFocusRecord(
            dateStr = todayStr,
            courseName = courseName,
            startTime = courseStartTime,
            endTime = courseEndTime,
            distractionDurationMills = 0L
        )

        // 核心：累加时长
        targetRecord.distractionDurationMills += addedDistractionMills

        // 3. 写入数据库
        box.put(targetRecord)
    }

    /**
     * 获取指定日期的所有课程记录（用于生成每日报告和红黑榜）
     * @param dateStr 目标日期，格式 "yyyy-MM-dd"
     */
    fun getDailyRecords(dateStr: String): List<ClassFocusRecord> {
        return box.query {
            equal(ClassFocusRecord_.dateStr, dateStr, QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.find()
    }

    /**
     * 计算某天总的摸鱼时长
     */
    fun getDailyTotalDistraction(dateStr: String): Long {
        val records = getDailyRecords(dateStr)
        return records.sumOf { it.distractionDurationMills }
    }
}