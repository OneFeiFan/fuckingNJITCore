package com.feifan.fuckingnjit.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TimeManager {
    fun calculateCurrentWeek(startDate: String, currentDate: String): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val start = sdf.parse(startDate)?.time as Long
        val today = sdf.parse(currentDate)?.time as Long

        val diff = (today - start) / (24 * 60 * 60 * 1000) // 毫秒转换为天

        return if (diff < 0) {
            1
        } else {
            (diff / 7).toInt() + 1
        }
    }
    fun dateChangeSimple(dateRange: Pair<String, String>, semesterStartDate: String): Map<String, List<String>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = sdf.parse(dateRange.first)
        val endDate = sdf.parse(dateRange.second)

        val calendar = Calendar.getInstance()
        if (startDate != null) {
            calendar.time = startDate
        }

        val weekAndDay = mutableMapOf<String, MutableList<String>>()

        while (calendar.time <= endDate) {
            val currentDateStr = sdf.format(calendar.time)
            val week = calculateCurrentWeek(semesterStartDate, currentDateStr)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1 (Sunday) to 7 (Saturday)

            // 调整为1-7对应周一到周日
            val adjustedDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1

            weekAndDay.getOrPut(week.toString()) { mutableListOf() }.add(adjustedDay.toString())
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return weekAndDay
    }
    fun getCurrentSchoolYear(): String {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar[Calendar.MONTH] // 获取当前月份（0-11）
        val currentYear = calendar[Calendar.YEAR] // 获取当前年份

        val schoolYearStart: Int
        val schoolYearEnd: Int
        val semester: Int

        if (currentMonth >= Calendar.SEPTEMBER) { // 如果当前月份大于或等于9月
            schoolYearStart = currentYear
            schoolYearEnd = currentYear + 1
            semester = 3 // 秋季学期
        } else { // 否则
            schoolYearStart = currentYear - 1
            schoolYearEnd = currentYear
            semester = 12 // 春季学期
        }
        // 教务系统采用的3表示第一学期，12表示第二学期，很有意思
        return "$schoolYearStart-$schoolYearEnd-$semester"
    }
}