package com.feifan.fuckingnjit.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TimeManager {
    private val SDF by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())}
    private val timeFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    private val DATELIST by lazy {
        arrayOf(
            timeFormat.parse("07:00"),
            timeFormat.parse("07:55"),
            timeFormat.parse("09:10"),
            timeFormat.parse("10:05"),
            timeFormat.parse("12:40"),
            timeFormat.parse("13:35"),
            timeFormat.parse("14:40"),
            timeFormat.parse("15:35"),
            timeFormat.parse("17:30"),
            timeFormat.parse("18:25"),
            timeFormat.parse("19:20")
        ).filterNotNull()  // 确保没有null值

    }
    fun isInLateNightPeriod(): Boolean {
        val millisPerDay = 24 * 60 * 60 * 1000L
        val currentMillis = System.currentTimeMillis() % millisPerDay

        val startMillis = 20 * 60 * 60 * 1000L // 20:00的毫秒数
        val endMillis = 5 * 60 * 60 * 1000L   // 05:00的毫秒数

        return currentMillis >= startMillis || currentMillis < endMillis
    }

    fun getDateList(): List<Date> {
        return DATELIST
    }
    fun calculateCurrentWeek(startDate: String, currentDate: String): Int {

        val start = SDF.parse(startDate)?.time as Long
        val today = SDF.parse(currentDate)?.time as Long

        val diff = (today - start) / (24 * 60 * 60 * 1000) // 毫秒转换为天

        return if (diff < 0) {
            1
        } else {
            (diff / 7).toInt() + 1
        }
    }
    fun calculateCurrentWeek(startDate: String): Int {

        // 获取当前日期
        val currentDate = SDF.format(Date())

        val start = SDF.parse(startDate)?.time as Long
        val today = SDF.parse(currentDate)?.time as Long

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
    fun todayWeekIndex(): Int {
        val weekIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2
        return if (weekIndex < 0) 6 else weekIndex
    }

}