package com.feifan.fuckingnjit.utils.database

import android.content.Context
import com.feifan.fuckingnjit.model.AppSystem
import com.feifan.fuckingnjit.model.ClassFocusRecord
import com.feifan.fuckingnjit.model.ClassFocusRecord_
import com.feifan.fuckingnjit.model.DailyRecord
import com.feifan.fuckingnjit.model.DailyRecord_
import com.feifan.fuckingnjit.model.MyObjectBox
import com.feifan.fuckingnjit.model.SleepSensorRecord
import com.feifan.fuckingnjit.model.SleepSensorRecord_
import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.model.User_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppDataCenter {
    private var boxStore: BoxStore? = null

    // 内部私有 Box 实例，对外绝对隔离
    private val systemBox by lazy { boxStore!!.boxFor(AppSystem::class.java) }
    private val userBox by lazy { boxStore!!.boxFor(User::class.java) }
    private val dailyBox by lazy { boxStore!!.boxFor(DailyRecord::class.java) }
    private val sensorBox by lazy { boxStore!!.boxFor(SleepSensorRecord::class.java) }

    // 🌟 [新增] 专注度明细 Box（专用于全局跨日查询）
    private val focusBox by lazy { boxStore!!.boxFor(ClassFocusRecord::class.java) }

    /**
     * 1. 唯一初始化入口 (在 CoreInitProvider 中调用即可)
     */
    fun init(context: Context) {
        if (boxStore == null) {
            boxStore = MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .name("core_database") // 核心修改：所有数据归拢到同一个物理文件夹！
                .build()
        }
    }

    fun getBoxStore(): BoxStore? = boxStore

    // ==========================================
    // 模块 1：App 全局系统配置 (替代 BaseDataBoxUtils)
    // ==========================================

    fun getSystemConfig(): AppSystem {
        var sys = systemBox.get(1L)
        if (sys == null) {
            sys = AppSystem(id = 1L)
            systemBox.put(sys)
        }
        return sys
    }

    fun updateSystemConfig(updater: (AppSystem) -> Unit) {
        val sys = getSystemConfig()
        updater(sys)
        systemBox.put(sys)
    }

    // ==========================================
    // 模块 2：用户与账号 (替代 UserBoxUtils & YiBanBoxUtils)
    // ==========================================

    fun getCurrentUser(): User? {
        val userId = getSystemConfig().currentUserId
        if (userId.isEmpty()) return null
        return userBox.query().equal(User_.id, userId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().findFirst()
    }

    fun saveUser(user: User) {
        userBox.put(user)
    }

    fun getAllUsers(): List<User> = userBox.all

    fun deleteUser(user: User) {
        userBox.remove(user)
    }

    // ==========================================
    // 模块 3：每日数据看板 (替代 SleepRecord/SP)
    // ==========================================

    fun getTodayRecord(): DailyRecord {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var record = dailyBox.query()
            .equal(DailyRecord_.dateStr, todayStr, QueryBuilder.StringOrder.CASE_SENSITIVE).build()
            .findFirst()
        if (record == null) {
            record = DailyRecord(dateStr = todayStr)
            dailyBox.put(record)
        }
        return record
    }

    fun updateTodayRecord(updater: (DailyRecord) -> Unit) {
        val record = getTodayRecord()
        updater(record)
        dailyBox.put(record)
    }

    // 🌟 [新增] 按日期获取指定的记录（用于数据同步服务回写以前的睡眠数据）
    fun getRecordByDate(dateStr: String): DailyRecord {
        var record = dailyBox.query()
            .equal(DailyRecord_.dateStr, dateStr, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
        if (record == null) {
            record = DailyRecord(dateStr = dateStr)
            dailyBox.put(record)
        }
        return record
    }

    fun saveSleepResult(dateStr: String, sleepStartMs: Long, wakeUpMs: Long, durationMins: Int) {
        // getRecordByDate 会自动处理查询和新日期的初始化
        val record = getRecordByDate(dateStr)

        record.sleepStartTimeMs = sleepStartMs
        record.wakeUpTimeMs = wakeUpMs
        record.totalSleepMinutes = durationMins

        // 执行持久化
        dailyBox.put(record)
    }

    // 🌟 [新增] 获取所有包含有效睡眠数据的历史记录，供 UI 和图表使用
    // 替代原先的 SleepRecordBoxUtils.getAllRecordsForUI()
    fun getValidSleepRecordsForUI(): List<DailyRecord> {
        return dailyBox.query()
            // 核心逻辑：只查 totalSleepMinutes 大于 0 的记录，过滤掉没同步睡眠的空白天数
            .greater(DailyRecord_.totalSleepMinutes, 0)
            .orderDesc(DailyRecord_.dateStr) // 按日期倒序排列，最近的在前
            .build()
            .find()
    }

    // ==========================================
    // 模块 4：专注度违规挂载 (替代 ClassFocusRecordBoxUtils)
    // ==========================================

    fun addDistractionTime(
        courseId: String,
        courseName: String,
        startTime: Long,
        endTime: Long,
        addedMills: Long
    ) {
        if (addedMills <= 0 || courseId.isEmpty()) return

        val todayRecord = getTodayRecord()

        // 在今天的记录下寻找这节课的明细
        var targetFocus = todayRecord.focusRecords.find {
            it.courseId == courseId && it.startTime == startTime
        }

        if (targetFocus == null) {
            targetFocus = ClassFocusRecord(
                courseId = courseId, courseName = courseName,
                startTime = startTime, endTime = endTime
            )
            // 建立双向绑定
            targetFocus.dailyRecord.target = todayRecord
            todayRecord.focusRecords.add(targetFocus)
        }

        // 累加数据并重置上传状态
        targetFocus.distractionDurationMills += addedMills
        targetFocus.isUploaded = false

        // ObjectBox 特性：保存父对象会自动保存所有挂载的子对象
        dailyBox.put(todayRecord)
    }

    // ==========================================
    // 模块 5：高频流水日志 (替代 SleepSensorBoxUtils)
    // ==========================================

    fun insertSensorBatch(records: List<SleepSensorRecord>) = sensorBox.put(records)

    fun clearOldSensorsBefore(timestamp: Long) {
        sensorBox.query().less(SleepSensorRecord_.timestamp, timestamp).build().remove()
    }

    fun getSensorRecordsBetween(start: Long, end: Long): List<SleepSensorRecord> {
        return sensorBox.query()
            .between(SleepSensorRecord_.timestamp, start, end)
            .order(SleepSensorRecord_.timestamp)
            .build()
            .find()
    }

    // ==========================================
    // 模块 6：数据同步专用
    // ==========================================

    /**
     * 获取所有尚未同步到服务端的专注度违规记录
     * 替代原 ClassFocusRecordBoxUtils.getUnuploadedRecords()
     */
    fun getUnuploadedFocusRecords(): List<ClassFocusRecord> {
        return focusBox.query()
            .equal(ClassFocusRecord_.isUploaded, false)
            .build()
            .find()
    }

    /**
     * 批量标记记录为已同步
     * 替代原 ClassFocusRecordBoxUtils.markAsUploaded()
     */
    fun markFocusRecordsAsUploaded(records: List<ClassFocusRecord>) {
        if (records.isEmpty()) return
        records.forEach { it.isUploaded = true }
        // ObjectBox 支持极速批量 put
        focusBox.put(records)
    }
}