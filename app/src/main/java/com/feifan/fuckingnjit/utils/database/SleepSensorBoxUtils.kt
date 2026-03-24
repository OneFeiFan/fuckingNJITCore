package com.feifan.fuckingnjit.utils.database

import com.feifan.fuckingnjit.model.SleepSensorRecord
import com.feifan.fuckingnjit.model.SleepSensorRecord_
import io.objectbox.Box

object SleepSensorBoxUtils : BaseBoxUtils(){
    override fun getDatabaseName() = "SLEEP_SENSOR"

    private val box: Box<SleepSensorRecord> by lazy {
        getBox(SleepSensorRecord::class.java)
    }

    /**
     * 批量插入数据 (极速)
     */
    fun insertBatch(records: List<SleepSensorRecord>) {
        box.put(records)
    }

    /**
     * 获取指定时间段内的数据（供 Python 导出时使用）
     */
    fun getRecordsBetween(startTime: Long, endTime: Long): List<SleepSensorRecord> {
        return box.query()
            .between(SleepSensorRecord_.timestamp, startTime, endTime)
            .order(SleepSensorRecord_.timestamp) // 按时间排序
            .build()
            .find()
    }

    /**
     * 清理 N 天前的数据，防止手机存储爆满
     */
    fun deleteRecordsBefore(timestamp: Long) {
        val oldRecords = box.query().less(SleepSensorRecord_.timestamp, timestamp)
            .build()
            .find()
        val deleteCount = oldRecords.size.toLong()
        if (deleteCount > 0) {
            box.remove(oldRecords)
        }
    }

    fun getAll(): List<SleepSensorRecord> {
        return box.all
    }
}