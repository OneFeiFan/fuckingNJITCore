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
import java.time.LocalDate

// APP大部分基础数据管理
object AppDataCenter {
    private var boxStore: BoxStore? = null

    // 内部私有 Box 实例
    private val systemBox by lazy { boxStore!!.boxFor(AppSystem::class.java) } // 和APP全局配置有关
    private val userBox by lazy { boxStore!!.boxFor(User::class.java) } // 和用户有关
    private val dailyBox by lazy { boxStore!!.boxFor(DailyRecord::class.java) } // 和每天健康数据有关
    private val sensorBox by lazy { boxStore!!.boxFor(SleepSensorRecord::class.java) } // 睡眠原始数据
    private val focusBox by lazy { boxStore!!.boxFor(ClassFocusRecord::class.java) } // 和课程专注度有关

    fun init(context: Context) {
        if (boxStore == null) {
            boxStore = MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .name("core_database") // 所有数据归拢到同一个物理文件夹！
                .build()
        }
    }

    fun getBoxStore(): BoxStore? = boxStore

    // 获取全局系统配置
    fun getSystemConfig(): AppSystem {
        var sys = systemBox.get(1L) // 全局数据默认分配id为1
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

    // 获取当前激活的用户
    fun getCurrentUser(): User? {
        val userId = getSystemConfig().currentUserId
        if (userId.isEmpty()) return null
        return userBox.query().equal(User_.id, userId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().findFirst()
    }

    // 增加用户或更新用户数据
    fun saveUser(user: User) {
        userBox.put(user)
    }

    fun getAllUsers(): List<User> = userBox.all

    fun deleteUser(user: User) {
        userBox.remove(user)
    }

    // 获取每日综合数据
    fun getTodayRecord(): DailyRecord {
        val todayStr = LocalDate.now().toString()
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

    // 基于日期获取记录
    fun getRecordByDate(dateStr: String): DailyRecord {
        var record = dailyBox.query()
            .equal(DailyRecord_.dateStr, dateStr, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
        if (record == null) {
            // 如果不存在数据对象，那么就返回空数据
            record = DailyRecord(dateStr = dateStr)
            dailyBox.put(record)
        }
        return record
    }

    // 保存睡眠数据
    // 分别是：日期、开始睡眠时间、醒来时间、睡眠时长。
    fun saveSleepResult(dateStr: String, sleepStartMs: Long, wakeUpMs: Long, durationMins: Int) {
        // getRecordByDate 会自动处理查询和新日期的初始化
        val record = getRecordByDate(dateStr)

        record.sleepStartTimeMs = sleepStartMs
        record.wakeUpTimeMs = wakeUpMs
        record.totalSleepMinutes = durationMins

        // 执行持久化
        dailyBox.put(record)
    }

    // 获取所有包含有效睡眠数据的历史记录，供 UI 和图表使用
    // 其实也不一定是给UI用（AI设计的
    fun getValidSleepRecordsForUI(): List<DailyRecord> {
        return dailyBox.query()
            // 只查 totalSleepMinutes 大于 0 的记录，过滤掉没同步睡眠的空白天数
            .greater(DailyRecord_.totalSleepMinutes, 0)
            .orderDesc(DailyRecord_.dateStr) // 按日期倒序排列，最近的在前
            .build()
            .find()
    }

    // 更新上课专注度数据
    fun addDistractionTime(
        courseId: String,
        courseName: String,
        startTime: Long,
        endTime: Long,
        addedMills: Long // 违规使用电子产品时间
    ) {
        if (addedMills <= 0 || courseId.isEmpty()) return

        val todayRecord = getTodayRecord()

        // 在今天的记录下寻找这节课的明细
        // 上课专注度是每日记录的一个子对象
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
        targetFocus.isUploaded = false //更新上传状态（false为待更新）

        // ObjectBox 特性：保存父对象会自动保存所有挂载的子对象
        dailyBox.put(todayRecord)
    }

    // 更新传感器数据（睡眠相关）
    fun insertSensorBatch(records: List<SleepSensorRecord>) = sensorBox.put(records)

    // 清空指定时间之前的传感器记录
    fun clearOldSensorsBefore(timestamp: Long) {
        sensorBox.query().less(SleepSensorRecord_.timestamp, timestamp).build().remove()
    }

    // 获取指定时间内的传感器数据
    fun getSensorRecordsBetween(start: Long, end: Long): List<SleepSensorRecord> {
        return sensorBox.query()
            .between(SleepSensorRecord_.timestamp, start, end)
            .order(SleepSensorRecord_.timestamp)
            .build()
            .find()
    }

    // 获取全部待更新的专注度数据
    fun getUnuploadedFocusRecords(): List<ClassFocusRecord> {
        return focusBox.query()
            .equal(ClassFocusRecord_.isUploaded, false)
            .build()
            .find()
    }

    // 将专注度数据批量变为已更新状态
    fun markFocusRecordsAsUploaded(records: List<ClassFocusRecord>) {
        if (records.isEmpty()) return
        records.forEach { it.isUploaded = true }
        // ObjectBox 支持极速批量 put
        focusBox.put(records)
    }
}