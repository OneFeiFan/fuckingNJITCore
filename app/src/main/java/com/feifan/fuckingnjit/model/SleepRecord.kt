package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 睡眠记录实体类 (用于 ObjectBox 存储)
 */
@Entity
data class SleepRecord(
    @Id var id: Long = 0,

    // 记录归属的日期，通常建议用“醒来的那一天”作为标志 (例如: "2024-05-20")
    // 这样方便查询“今天的睡眠数据”
    var targetDate: String = "",

    // 实际入睡时间戳 (毫秒) - 通过你另外一个 Demo 里的最后一次锁屏时间获取
    var sleepStartTimeMs: Long = 0L,

    // 实际醒来时间戳 (毫秒) - 通过早晨第一次解锁手机时间获取
    var wakeUpTimeMs: Long = 0L,

    // 总睡眠时长 (分钟) - 存入数据库方便直接做统计和均值计算
    var totalSleepMinutes: Int = 0
) {
    /**
     * 辅助方法：当你拿到入睡和醒来时间戳后，调用此方法自动计算并填充时长
     */
    fun calculateAndSetDuration() {
        if (wakeUpTimeMs > sleepStartTimeMs && sleepStartTimeMs > 0L) {
            val diffMs = wakeUpTimeMs - sleepStartTimeMs
            totalSleepMinutes = (diffMs / (1000 * 60)).toInt()
        } else {
            totalSleepMinutes = 0
        }
    }

    /**
     * 辅助方法：自动根据醒来时间生成 targetDate (格式：yyyy-MM-dd)
     */
    fun generateTargetDate() {
        if (wakeUpTimeMs > 0L) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            targetDate = sdf.format(Date(wakeUpTimeMs))
        }
    }
}