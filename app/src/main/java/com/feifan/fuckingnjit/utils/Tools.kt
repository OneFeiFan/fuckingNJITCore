package com.feifan.fuckingnjit.utils

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 通用工具方法集合
 */
object Tools {
    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 获取目标睡眠时间计算窗口
     *
     * 返回昨天中午 12:00 到今天中午 12:00 的时间戳范围，
     * 用于从传感器数据中筛选出属于"昨晚睡眠"的数据段。
     *
     * @return Pair(起始时间戳毫秒, 结束时间戳毫秒)
     */
    fun getTargetSleepWindow(): Pair<Long, Long> {
        val todayNoon = LocalDate.now().atTime(12, 0)
        val yesterdayNoon = todayNoon.minusDays(1)
        val zoneId = ZoneId.systemDefault()
        return Pair(
            yesterdayNoon.atZone(zoneId).toInstant().toEpochMilli(),
            todayNoon.atZone(zoneId).toInstant().toEpochMilli()
        )
    }

//    fun isInLateNightPeriod(): Boolean {
//        val currentHour = LocalTime.now().hour
//        return currentHour >= 20 || currentHour < 5
//    }

    /** 获取今天是本周的第几天（周一为起始点，返回 0~6） */
    fun todayWeekIndex(): Int {
        return LocalDate.now().dayOfWeek.value - 1
    }

    /**
     * 将日期范围转换为周次 → 星期列表的映射
     *
     * 用于空教室查询接口中日期到教学周的转换。
     *
     * @param dateRange 起止日期字符串对
     * @param semesterStartDate 学期开始日期
     * @return 以周次为键、星期几列表为值的映射表
     */
    fun dateChangeSimple(
        dateRange: Pair<String, String>,
        semesterStartDate: String
    ): Map<String, List<String>> {
        val startDate = LocalDate.parse(dateRange.first, FORMATTER)
        val endDate = LocalDate.parse(dateRange.second, FORMATTER)

        val weekAndDay = mutableMapOf<String, MutableList<String>>()
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            val currentDateStr = currentDate.format(FORMATTER)
            // 调用新的 EduScheduleConfig 获取周次
            val week = EduScheduleConfig.calculateWeek(semesterStartDate, currentDateStr)
            val adjustedDay = currentDate.dayOfWeek.value

            weekAndDay.getOrPut(week.toString()) { mutableListOf() }.add(adjustedDay.toString())
            currentDate = currentDate.plusDays(1)
        }

        return weekAndDay
    }
}