package com.feifan.fuckingnjit.utils


import com.feifan.fuckingnjit.utils.database.AppDataCenter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TimeManager private constructor() {

    companion object {
        private val instance_: TimeManager by lazy { TimeManager() }

        fun getInstance(): TimeManager = instance_

        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    fun getTargetSleepWindow(): Pair<Long, Long> {
        // 锚点一：获取今天的 12:00:00
        val todayNoon = LocalDate.now().atTime(12, 0)
        // 锚点二：获取昨天的 12:00:00
        val yesterdayNoon = todayNoon.minusDays(1)

        val zoneId = ZoneId.systemDefault()
        return Pair(
            yesterdayNoon.atZone(zoneId).toInstant().toEpochMilli(),
            todayNoon.atZone(zoneId).toInstant().toEpochMilli()
        )
    }

    fun getSemesterStartDate(): String {
        return "2025-02-17"
        val dateMs = AppDataCenter.getSystemConfig().semesterStartDateMs
        return try {
            if (dateMs != 0L) {
                Instant.ofEpochMilli(dateMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(FORMATTER)
            } else {
                "2025-02-17"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "2025-02-17"
        }
    }

    fun isInLateNightPeriod(): Boolean {
        val currentHour = LocalTime.now().hour
        return currentHour >= 20 || currentHour < 5
    }

    fun calculateCurrentWeek(startDate: String, currentDate: String): Int {
        val start = LocalDate.parse(startDate, FORMATTER)
        val today = LocalDate.parse(currentDate, FORMATTER)

        val diffDays = ChronoUnit.DAYS.between(start, today)

        return if (diffDays < 0) {
            1
        } else {
            (diffDays / 7).toInt() + 1
        }
    }

    fun calculateCurrentWeek(startMs: Long): Int {
        val start = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        // 保留你原有的减去365天的业务逻辑
        val today = LocalDate.now().minusDays(365)

        val diffDays = ChronoUnit.DAYS.between(start, today)

        return if (diffDays < 0) {
            1
        } else {
            (diffDays / 7).toInt() + 1
        }
    }

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
            val week = calculateCurrentWeek(semesterStartDate, currentDateStr)

            val adjustedDay = currentDate.dayOfWeek.value

            weekAndDay.getOrPut(week.toString()) { mutableListOf() }.add(adjustedDay.toString())
            currentDate = currentDate.plusDays(1) // 原 calendar.add 的现代化写法
        }

        return weekAndDay
    }

    fun getCurrentSchoolYear(): String {
        val today = LocalDate.now()
        val month = today.monthValue // 直接获取 1..12
        val year = today.year
        val day = today.dayOfMonth

        val schoolYearStart: Int
        val schoolYearEnd: Int
        val semester: Int

        // 逻辑：7月(原代码是JULY, 但值为6, java.time 中是7)及以后，进入新学年，属于第一学期 (3)
        if (month >= 7) {
            schoolYearStart = year
            schoolYearEnd = year + 1
            semester = 3
        }
        // 逻辑：1月通常还在第一学期期末 (3)
        else if (month == 1) {
            if (day >= 15) {
                schoolYearStart = year - 1
                schoolYearEnd = year
                semester = 12
            } else {
                schoolYearStart = year - 1
                schoolYearEnd = year
                semester = 3
            }
        }
        // 逻辑：2月到6月，属于上一学年的第二学期 (12)
        else {
            schoolYearStart = year - 2
            schoolYearEnd = year - 1
            semester = 12
        }

        return "$schoolYearStart-$schoolYearEnd-$semester"
    }

    fun todayWeekIndex(): Int {
        return LocalDate.now().dayOfWeek.value - 1
    }

}