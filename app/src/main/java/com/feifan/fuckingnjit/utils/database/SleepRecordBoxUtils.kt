package com.feifan.fuckingnjit.utils.database

import com.feifan.fuckingnjit.model.SleepRecord
import com.feifan.fuckingnjit.model.SleepRecord_
import io.objectbox.Box
import io.objectbox.query.QueryBuilder

object SleepRecordBoxUtils : BaseBoxUtils() {

    // 懒加载获取 ObjectBox 的 Box 实例
    private val box: Box<SleepRecord> by lazy {
        getBox(SleepRecord::class.java)
    }

    /**
     * 根据目标日期获取当天的睡眠记录
     * * @param targetDate 日期字符串，例如 "2026-03-24"
     * @return 返回找到的实体，如果没有则返回 null
     */
    fun getByDate(targetDate: String): SleepRecord? {
        return box.query()
            .equal(SleepRecord_.targetDate, targetDate, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .findFirst()
    }

    /**
     * 插入或更新一条睡眠记录
     * * ObjectBox 非常智能，如果你传入的 record.id 是 0，它会创建新数据并赋予自增 ID；
     * 如果 record.id 是一个已存在的值（比如通过 getByDate 查出来的对象），它会直接覆盖更新。
     */
    fun insertOrUpdate(record: SleepRecord) {
        box.put(record)
    }

    /**
     * [可选补充] 获取所有睡眠记录，供你的 UI 层绘制历史睡眠柱状图或折线图使用
     */
    fun getAllRecordsForUI(): List<SleepRecord> {
        return box.query()
            .orderDesc(SleepRecord_.targetDate) // 按日期倒序，让最近的记录排在最前面
            .build()
            .find()
    }

    /**
     * [可选补充] 清空所有统计数据（如果用户需要重置）
     */
    fun deleteAll() {
        box.removeAll()
    }

    override fun getDatabaseName() = "SLEEP_RECORD_STATISTICS"
}