package com.feifan.fuckingnjit.utils

import java.util.Calendar

// TimeUtils.kt (伪代码)
object TimeUtils {
    // 判断今天晚上是否算节假日（周五、周六晚上算节假日，周日晚上算工作日）
    fun isTonightHoliday(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.SATURDAY
    }

    /**
     * 计算当前时间距离指定的起床时间还有多少分钟
     * @param targetTime 格式如 "06:50"
     */
    fun getMinutesUntilWakeUp(targetTime: String): Int {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        // 解析目标时间
        val parts = targetTime.split(":")
        val targetHour = parts[0].toInt()
        val targetMinute = parts[1].toInt()

        target.set(Calendar.HOUR_OF_DAY, targetHour)
        target.set(Calendar.MINUTE, targetMinute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // 【核心逻辑】如果目标时间比现在早（比如现在23点，目标6点），说明是明天的6点
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val diffMillis = target.timeInMillis - now.timeInMillis
        return (diffMillis / (1000 * 60)).toInt() // 转换为分钟
    }

    // 测试用例：
    // 当前 23:00，目标 "06:50" -> 返回 470 (7小时50分)
    // 当前 06:00，目标 "06:50" -> 返回 50
}