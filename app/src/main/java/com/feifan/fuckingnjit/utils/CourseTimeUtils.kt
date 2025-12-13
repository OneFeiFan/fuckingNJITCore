package com.feifan.fuckingnjit.utils

import java.time.LocalTime

// 请根据你们学校的作息时间修改 timeMap
object CourseTimeUtils {
    // 节次 -> 开始时间 (HH:mm)
    private val startTimes = mapOf(
        1 to "08:00", 2 to "08:55", 3 to "10:10", 4 to "11:05",
        5 to "13:40", 6 to "14:35", 7 to "15:40", 8 to "16:35",
        9 to "18:30", 10 to "19:25", 11 to "20:20"
    )

    // 节次 -> 结束时间 (HH:mm)
    private val endTimes = mapOf(
        1 to "08:45", 2 to "09:40", 3 to "10:55", 4 to "11:50",
        5 to "14:25", 6 to "15:20", 7 to "16:25", 8 to "17:20",
        9 to "19:15", 10 to "20:10", 11 to "21:05"
    )

    /**
     * 获取用于显示的具体时间段字符串
     * @param startNode 开始节次 (如 1)
     * @param step 持续节次 (如 2)
     * @return 例如 "08:00-09:40"
     */
    fun getDisplayTime(startNode: Int, step: Int): String {
        val endNode = startNode + step - 1
        val s = startTimes[startNode] ?: "00:00"
        val e = endTimes[endNode] ?: "00:00"
        return "$s-$e"
    }

    /**
     * 获取课程结束的 LocalTime 对象，用于判断课程是否已过期
     */
    fun getCourseEndTime(startNode: Int, step: Int): LocalTime {
        val endNode = startNode + step - 1
        val timeStr = endTimes[endNode] ?: "23:59"
        // 补全格式 HH:mm
        val formatted = if (timeStr.length == 4) "0$timeStr" else timeStr
        return try {
            LocalTime.parse(formatted)
        } catch (e: Exception) {
            LocalTime.MAX
        }
    }
}