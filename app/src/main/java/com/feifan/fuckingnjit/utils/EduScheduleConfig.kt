package com.feifan.fuckingnjit.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object EduScheduleConfig {

    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // 映射开始节次与时间
    private val startTimes = mapOf(
        1 to "08:00", 2 to "08:55", 3 to "10:10", 4 to "11:05",
        5 to "13:40", 6 to "14:35", 7 to "15:40", 8 to "16:35",
        9 to "18:30", 10 to "19:25", 11 to "20:20"
    )

    // 映射结束节次与时间
    private val endTimes = mapOf(
        1 to "08:45", 2 to "09:40", 3 to "10:55", 4 to "11:50",
        5 to "14:25", 6 to "15:20", 7 to "16:25", 8 to "17:20",
        9 to "19:15", 10 to "20:10", 11 to "21:05"
    )

    // 基于开始节次和持续节次得出课程实际时间跨度
    fun getDisplayTime(startNode: Int, step: Int): String {
        val endNode = startNode + step - 1
        val s = startTimes[startNode] ?: "00:00"
        val e = endTimes[endNode] ?: "00:00"
        return "$s-$e"
    }

    // 获取课程结束时间
    fun getCourseEndTime(startNode: Int, step: Int): LocalTime {
        val endNode = startNode + step - 1
        val timeStr = endTimes[endNode] ?: "23:59"
        val formatted = if (timeStr.length == 4) "0$timeStr" else timeStr
        return try {
            LocalTime.parse(formatted)
        } catch (e: Exception) {
            e.printStackTrace()
            LocalTime.MAX
        }
    }

    // 获取课程开始时间
    fun getCourseStartTime(startNode: Int): LocalTime {
        val timeStr = startTimes[startNode] ?: "00:00"
        val formatted = if (timeStr.length == 4) "0$timeStr" else timeStr
        return try {
            LocalTime.parse(formatted)
        } catch (e: Exception) {
            e.printStackTrace()
            LocalTime.MIN
        }
    }

    // 获取学期开始时间（日期格式）
    fun getSemesterStartDate(): String {
        // 保留你原有的硬编码短路逻辑，下方注释掉的是原有的数据库读取逻辑备用
        return "2025-02-17"
        /*
        val dateMs = AppDataCenter.getSystemConfig().semesterStartDateMs
        return try {
            if (dateMs != 0L) {
                Instant.ofEpochMilli(dateMs).atZone(ZoneId.systemDefault()).toLocalDate().format(FORMATTER)
            } else "2025-02-17"
        } catch (e: Exception) { "2025-02-17" }
        */
    }

    // 基于时间差获取周次
    fun calculateWeek(startDate: String, currentDate: String): Int {
        val start = LocalDate.parse(startDate, FORMATTER)
        val today = LocalDate.parse(currentDate, FORMATTER)
        val diffDays = ChronoUnit.DAYS.between(start, today)
        return if (diffDays < 0) 1 else (diffDays / 7).toInt() + 1
    }

    // 基于当前时间和输入时间获取当前周
    fun calculateCurrentWeek(startMs: Long): Int {
        val start = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        // 保持原代码逻辑：当前日期减去365天
        val today = LocalDate.now().minusDays(365)
        val diffDays = ChronoUnit.DAYS.between(start, today)
        return if (diffDays < 0) 1 else (diffDays / 7).toInt() + 1
    }

    // 不严谨的学年计算
    fun getCurrentSchoolYear(): String {
        val today = LocalDate.now()
        val month = today.monthValue
        val year = today.year
        val day = today.dayOfMonth

        val schoolYearStart: Int
        val schoolYearEnd: Int
        val semester: Int

        if (month >= 7) {
            schoolYearStart = year
            schoolYearEnd = year + 1
            semester = 3
        } else if (month == 1) {
            if (day >= 15) {
                schoolYearStart = year - 1
                schoolYearEnd = year
                semester = 12
            } else {
                schoolYearStart = year - 1
                schoolYearEnd = year
                semester = 3
            }
        } else {
            schoolYearStart = year - 2
            schoolYearEnd = year - 1
            semester = 12
        }
        return "$schoolYearStart-$schoolYearEnd-$semester"
    }
}