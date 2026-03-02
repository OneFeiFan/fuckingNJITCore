package com.feifan.fuckingnjit.utils

import com.feifan.fuckingnjit.model.AppMode
import com.feifan.fuckingnjit.model.SleepRecord
import java.util.Calendar

object SmartSleepAdvisor {

    /**
     * 核心算法：获取今晚【动态自适应】后的最晚入睡时间
     */
    fun getAdaptiveLatestSleepTime(
        mode: AppMode,
        isTonightHoliday: Boolean,
        recentSleepRecords: List<SleepRecord> // 传入最近 3-7 天的睡眠记录
    ): String {
        // 1. 获取该模式的静态基准时间
        val baseSleepTime = mode.getTargetSleepTime(isTonightHoliday)

        if (recentSleepRecords.isEmpty()) {
            return baseSleepTime // 没有历史数据，直接返回静态配置
        }

        // 2. 计算理想睡眠时长 (以分钟计)
        val idealSleepMinutes = calculateDuration(baseSleepTime, mode.suggestedWakeTime)

        // 3. 计算用户近期的平均实际睡眠时长
        val actualAvgMinutes = recentSleepRecords.map { it.totalSleepMinutes }.average()

        // 4. 计算“睡眠欠债” (缺了多少分钟)
        val sleepDebt = idealSleepMinutes - actualAvgMinutes

        // 5. 动态计算偏移量 (最大调整幅度限制在 45 分钟内，避免作息剧烈震荡)
        var offsetMinutes = 0
        if (sleepDebt > 30) {
            // 欠觉超过半小时，需要提早睡觉来补偿
            // 补偿系数设为 0.5（比如欠了60分钟，今晚提早30分钟睡）
            offsetMinutes = -((sleepDebt * 0.5).coerceAtMost(45.0).toInt())
        } else if (sleepDebt < -60) {
            // 睡得比理想还多1小时以上，说明精力极其充沛，可以稍微放宽今晚的入睡限制
            offsetMinutes = 15 // 允许晚睡15分钟
        }

        // 6. 将基准时间加上偏移量，返回新的时间
        return applyOffset(baseSleepTime, offsetMinutes)
    }

    // --- 辅助方法 ---
    private fun applyOffset(timeStr: String, offsetMins: Int): String {
        val parts = timeStr.split(":")
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            add(Calendar.MINUTE, offsetMins)
        }
        val newHour = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val newMin = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$newHour:$newMin"
    }

    private fun calculateDuration(sleepTime: String, wakeTime: String): Int {
        // 伪代码：计算如 "23:30" 到 "06:00" 是 390 分钟
        return 390
    }
}