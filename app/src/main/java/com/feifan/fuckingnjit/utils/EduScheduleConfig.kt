package com.feifan.fuckingnjit.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 教学日程配置工具类
 *
 * 维护学校每节课的开始/结束时间映射表，
 * 提供节次与物理时间的互转、学年学期计算、周次计算等基础能力。
 */
object EduScheduleConfig {

    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 节次 → 课程开始时间映射表 */
    private val startTimes = mapOf(
        1 to "08:00", 2 to "08:55", 3 to "10:10", 4 to "11:05",
        5 to "13:40", 6 to "14:35", 7 to "15:40", 8 to "16:35",
        9 to "18:30", 10 to "19:25", 11 to "20:20"
    )

    /** 节次 → 课程结束时间映射表 */
    private val endTimes = mapOf(
        1 to "08:45", 2 to "09:40", 3 to "10:55", 4 to "11:50",
        5 to "14:25", 6 to "15:20", 7 to "16:25", 8 to "17:20",
        9 to "19:15", 10 to "20:10", 11 to "21:05"
    )

    /**
     * 根据开始节次和持续节次生成显示用的课程时间段文本
     *
     * @param startNode 开始节次（1~11）
     * @param step 持续节数
     * @return 格式如 "08:00-08:45" 的字符串
     */
    fun getDisplayTime(startNode: Int, step: Int): String {
        val endNode = startNode + step - 1
        val s = startTimes[startNode] ?: "00:00"
        val e = endTimes[endNode] ?: "00:00"
        return "$s-$e"
    }

    /**
     * 根据开始节次和持续节数获取课程结束时间
     *
     * @param startNode 开始节次
     * @param step 持续节数
     * @return 课程结束的 LocalTime，解析失败时返回 LocalTime.MAX
     */
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

    /**
     * 根据节次获取课程开始时间
     *
     * @param startNode 节次（1~11）
     * @return 对应的 LocalTime，解析失败时返回 LocalTime.MIN
     */
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

    /** 获取当前学期的开始日期字符串 */
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

    /**
     * 基于两个日期计算周次
     *
     * @param startDate 学期开始日期
     * @param currentDate 目标日期
     * @return 教学周次（从 1 开始），目标日期早于开始日期时返回 1
     */
    fun calculateWeek(startDate: String, currentDate: String): Int {
        val start = LocalDate.parse(startDate, FORMATTER)
        val today = LocalDate.parse(currentDate, FORMATTER)
        val diffDays = ChronoUnit.DAYS.between(start, today)
        return if (diffDays < 0) 1 else (diffDays / 7).toInt() + 1
    }

    /**
     * 根据学期开始时间戳计算当前教学周次
     *
     * @param startMs 学期开始的 Unix 时间戳（毫秒）
     * @return 当前教学周次
     */
    fun calculateCurrentWeek(startMs: Long): Int {
        val start = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        // 保持原代码逻辑：当前日期减去365天
        val today = LocalDate.now().minusDays(365)
        val diffDays = ChronoUnit.DAYS.between(start, today)
        return if (diffDays < 0) 1 else (diffDays / 7).toInt() + 1
    }

    /**
     * 计算当前学年学期标识符
     *
     * 返回格式为 "起始年-结束年-学期码"，其中学期码 3 表示第一学期、12 表示第二学期。
     * 注意：该实现为简化版本，边界判断不够精确。
     *
     * @return 如 "2024-2025-3" 的学年学期字符串
     */
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