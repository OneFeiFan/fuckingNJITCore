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
    override fun getDatabaseName() = "FOCUS_RECORD"

    /**
     * 增量记录摸鱼时长（核心逻辑）
     * @param courseName 当前课程名称
     * @param courseStartTime 课程开始时间戳
     * @param courseEndTime 课程结束时间戳
     * @param addedDistractionMills 本次新增的违规时长（毫秒）
     */
    /**
     * 增量记录摸鱼时长
     * @param courseId 教务系统课程唯一ID（服务端聚合的锚点）
     */
    fun addDistractionTime(
        courseId: String,
        courseName: String,
        courseStartTime: Long,
        courseEndTime: Long,
        addedDistractionMills: Long
    ) {
        if (addedDistractionMills <= 0 || courseId.isEmpty()) return

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 1. 精准定位：改用 courseId 和 startTime 进行唯一性确认
        val existingRecord = box.query {
            equal(ClassFocusRecord_.dateStr, todayStr, QueryBuilder.StringOrder.CASE_SENSITIVE)
            equal(ClassFocusRecord_.courseId, courseId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            equal(ClassFocusRecord_.startTime, courseStartTime)
        }.findFirst()

        // 2. 增量更新或新建记录
        val targetRecord = existingRecord ?: ClassFocusRecord(
            dateStr = todayStr,
            courseId = courseId,
            courseName = courseName,
            startTime = courseStartTime,
            endTime = courseEndTime,
            distractionDurationMills = 0L,
            isUploaded = false // 新记录默认未上传
        )

        // 核心：累加时长
        targetRecord.distractionDurationMills += addedDistractionMills

        // 如果一条已经上传过的记录又产生了新的摸鱼时长，需重置为未上传状态，以便服务端做增量或覆盖更新
        targetRecord.isUploaded = false

        box.put(targetRecord)
    }

    // ================== 同步专用方法 ==================

    /**
     * 获取所有尚未同步到服务端的记录
     */
    fun getUnuploadedRecords(): List<ClassFocusRecord> {
        return box.query {
            equal(ClassFocusRecord_.isUploaded, false)
        }.find()
    }

    /**
     * 批量标记记录为已同步
     */
    fun markAsUploaded(records: List<ClassFocusRecord>) {
        if (records.isEmpty()) return
        records.forEach { it.isUploaded = true }
        box.put(records) // ObjectBox 支持批量 put，效率很高
    }
}