package com.feifan.fuckingnjit.model

// 顶层响应体
data class DashboardResponse(
    val currentMode: String,
    val overallScore: Int,
    val factors: DecisionFactors,
    val actionableInsight: ActionableInsight,
    val timeline: Timeline,
    val rawStats: RawStats
)

// 三大核心因子
data class DecisionFactors(
    val courseStress: Int,
    val physicalState: Int,
    val focusCost: Int
)

// 动态干预建议
data class ActionableInsight(
    val show: Boolean,
    val level: String, // 枚举值: "critical", "warning", "info"
    val title: String,
    val message: String
)

// 睡眠与课程时间线
data class Timeline(
    val targetSleepTime: String,
    val offset: String,
    val courses: List<TimelineCourse>
)

data class TimelineCourse(
    val time: String,
    val name: String
)

// 原始统计溯源数据
data class RawStats(
    val sleepDurationStr: String,
    val steps: Int,
    val targetSteps: Int,
    val focusRate: Int,
    val distractionMins: Int
)