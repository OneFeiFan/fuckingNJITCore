package com.feifan.fuckingnjit.decision

// 封装了当日综合评估结果，供前端 Dashboard UI 渲染使用
data class DashboardResponse(
    val currentMode: String,// 当前运行模式标识字符串
    val overallScore: Int, // 综合评分
    val factors: DecisionFactors,// 包含三大核心决策因子
    val actionableInsight: ActionableInsight,// 动态干预建议
    val timeline: Timeline,// 睡眠目标与今日课程的时间轴视图数据
    val rawStats: RawStats// 原始统计数据溯源
)

// 决策因子
data class DecisionFactors(
    val courseStress: Int,//课程压力因子
    val physicalState: Int,//身体状态因子
    val focusCost: Int//专注成本因子
)

// 动态干预建议
data class ActionableInsight(
    val show: Boolean,// 是否展示该建议
    val level: String, // 枚举值: "critical", "warning", "info"
    val title: String,// 卡片标题
    val message: String// 卡片正文
)

// 时间线
data class Timeline(
    val targetSleepTime: String,// 目标入睡时间，格式 HH:mm
    val offset: String, // 相对当前时间的偏移描述
    val courses: List<TimelineCourse> // 今日课程列表
)

// 时间线中的单条课程条目
data class TimelineCourse(
    val time: String,// 上课时间段
    val name: String// 课程名称
)

// 原始统计数据
data class RawStats(
    val sleepDurationStr: String,//睡眠时长的可读字符串表示
    val steps: Int,// 今日累计步数
    val targetSteps: Int,// 每日步数目标值
    val focusRate: Int,//专注率
    val distractionMins: Int//累计分心时长
)