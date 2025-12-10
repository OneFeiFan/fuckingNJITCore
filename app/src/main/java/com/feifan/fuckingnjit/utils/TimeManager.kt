package com.feifan.fuckingnjit.utils

import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TimeManager private constructor() {

    companion object {
        private val instance_: TimeManager by lazy { TimeManager() }

        fun getInstance(): TimeManager = instance_
    }

    private val SDF by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
//    private val DATELIST by lazy {
//        val today = LocalDate.now() // 今天的日期
//        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm") // 时间格式化器
//        arrayOf(
//            "07:00", "07:55", "09:10", "10:05", "12:40",
//            "13:35", "14:40", "15:35", "17:30", "18:25", "19:20"
//        ).mapNotNull { timeStr ->
//            val time = LocalTime.parse(timeStr, timeFormatter) // 解析时间
//            // 合并为今天的日期 + 指定时间
//            today.atTime(time)
//                .atZone(ZoneId.systemDefault())
//                .toInstant()
//                .let { Date.from(it) } // 转为旧版 Date（如需兼容旧代码）
//        }
//    }

    fun getSemesterStartDate(): String {
        val date = BaseDataBoxUtils.getSemesterStartDate()
        try {
            if (date != 0L) {
                return SDF.format(Date(date))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "2025-02-17"
    }

    fun isInLateNightPeriod(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return currentHour >= 20 || currentHour < 5
    }


//    fun getDateList(): List<Date> {
//        return DATELIST
//    }

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

    fun calculateCurrentWeek(start: Long): Int {
        // 获取当前日期
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val diff = (today - start) / (24 * 60 * 60 * 1000) // 毫秒转换为天

        return if (diff < 0) {
            1
        } else {
            (diff / 7).toInt() + 1
        }
    }

    fun dateChangeSimple(
        dateRange: Pair<String, String>,
        semesterStartDate: String
    ): Map<String, List<String>> {
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

        if (currentMonth >= Calendar.JULY) { // 如果当前月份大于或等于6月
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