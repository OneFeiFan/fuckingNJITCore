package com.feifan.fuckingnjit.utils

import java.util.Calendar

/**
 * 时间策略管理器
 * 支持添加多个时间段（精确到分钟），支持跨天
 */
class TimeStrategyManager {

    // 内部类：表示一个时间段
    private data class TimeRange(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int
    ) {
        // 将时间转换为“从午夜开始的分钟数”进行比较
        private val startTotal = startHour * 60 + startMinute
        private val endTotal = endHour * 60 + endMinute

        fun contains(currentHour: Int, currentMinute: Int): Boolean {
            val currentTotal = currentHour * 60 + currentMinute

            return if (startTotal <= endTotal) {
                // 普通时间段 (例如 09:00 - 18:00)
                currentTotal in startTotal..endTotal
            } else {
                // 跨天时间段 (例如 23:00 - 02:00)
                currentTotal >= startTotal || currentTotal <= endTotal
            }
        }

        override fun toString(): String {
            return String.format("%02d:%02d-%02d:%02d", startHour, startMinute, endHour, endMinute)
        }
    }

    private val ranges = mutableListOf<TimeRange>()

    /**
     * 添加允许运行的时间段
     */
    fun addRange(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        ranges.add(TimeRange(startHour, startMinute, endHour, endMinute))
    }

    /**
     * 清空所有规则
     */
    fun clearRanges() {
        ranges.clear()
    }

    /**
     * 检查当前时间是否在任意一个允许的范围内
     * 如果没有设置任何规则，默认允许（或者你可以改为默认禁止）
     */
    fun isCurrentTimeAllowed(): Boolean {
        if (ranges.isEmpty()) {
            return true // 默认全天允许，或者改为 false
        }

        val cal = Calendar.getInstance()
        val curH = cal.get(Calendar.HOUR_OF_DAY)
        val curM = cal.get(Calendar.MINUTE)

        // 只要符合任意一个时间段即可
        return ranges.any { it.contains(curH, curM) }
    }

    /**
     * 获取当前生效的规则描述（用于调试或通知显示）
     */
    fun getActiveRangesDescription(): String {
        if (ranges.isEmpty()) return "全天候"
        return ranges.joinToString(", ") { it.toString() }
    }
}