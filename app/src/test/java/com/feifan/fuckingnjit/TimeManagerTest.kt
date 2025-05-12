package com.feifan.fuckingnjit


import com.feifan.fuckingnjit.utils.TimeManager
import org.junit.Assert
import org.junit.Test

class TimeManagerTest {

    @Test
    fun testCalculateCurrentWeek_正常情况() {
        // 测试正常情况，当前日期大于开始日期
        val timeManager = TimeManager()
        val startDate = "2025-02-17"
        val currentDate = "2025-05-06"
        Assert.assertEquals(12, timeManager.calculateCurrentWeek(startDate, currentDate))
    }

    @Test
    fun testCalculateCurrentWeek_当前日期等于开始日期() {
        // 测试当前日期等于开始日期
        val timeManager = TimeManager()
        val startDate = "2025-02-17"
        val currentDate = "2025-02-17"
        Assert.assertEquals(1, timeManager.calculateCurrentWeek(startDate, currentDate))
    }

    @Test
    fun testCalculateCurrentWeek_当前日期小于开始日期() {
        // 测试当前日期小于开始日期
        val timeManager = TimeManager()
        val startDate = "2025-02-17"
        val currentDate = "2024-02-01"
        Assert.assertEquals(1, timeManager.calculateCurrentWeek(startDate, currentDate))
    }

    @Test
    fun testDateChangeSimple_正常情况() {
        // 测试正常情况，日期范围和学期开始日期
        val timeManager = TimeManager()
        val dateRange = Pair("2025-05-05", "2025-05-06")
        val semesterStartDate = "2025-02-17"
        val expected = mapOf(
            12 to listOf(1, 2)
        )
        Assert.assertEquals(expected, timeManager.dateChangeSimple(dateRange, semesterStartDate))
    }

    @Test
    fun testDateChangeSimple_开始日期在学期开始日期之前() {
        // 测试开始日期在学期开始日期之前
        val timeManager = TimeManager()
        val dateRange = Pair("2025-01-16", "2025-01-16")
        val semesterStartDate = "2025-02-17"
        val expected = mapOf(
            1 to listOf(4)//实际这个时间没有意义
        )
        Assert.assertEquals(expected, timeManager.dateChangeSimple(dateRange, semesterStartDate))
    }

    @Test
    fun testDateChangeSimple_开始日期和结束日期相同() {
        // 测试开始日期和结束日期相同
        val timeManager = TimeManager()
        val dateRange = Pair("2025-02-17", "2025-02-17")
        val semesterStartDate = "2025-02-17"
        val expected = mapOf(
            1 to listOf(1)
        )
        Assert.assertEquals(expected, timeManager.dateChangeSimple(dateRange, semesterStartDate))
    }



    @Test
    fun testDateChangeSimple_日期范围跨越多周() {
        // 测试日期范围跨越多周
        val timeManager = TimeManager()
        val dateRange = Pair("2025-05-01", "2025-05-11")
        val semesterStartDate = "2025-02-17"
        val expected = mapOf(
            11 to listOf(4, 5,6,7),
            12 to listOf(1, 2, 3,4,5,6,7),
        )
        Assert.assertEquals(expected, timeManager.dateChangeSimple(dateRange, semesterStartDate))
    }
}
