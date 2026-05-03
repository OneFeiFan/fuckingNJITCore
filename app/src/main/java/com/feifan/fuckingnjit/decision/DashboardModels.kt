package com.feifan.fuckingnjit.decision

/**
 * 当日综合评估结果，供前端 Dashboard UI 渲染使用
 *
 * @param currentMode 当前运行模式标识字符串
 * @param overallScore 综合评分（0~100）
 * @param factors 三大核心决策因子
 * @param actionableInsight 动态干预建议卡片
 * @param timeline 睡眠目标与明日课程的时间轴视图数据
 * @param rawStats 原始统计数据溯源
 * @param alarmInfo 闹钟信息，UI 层据此调用系统闹钟
 */
data class DashboardResponse(
    val currentMode: String,
    val overallScore: Int,
    val factors: DecisionFactors,
    val actionableInsight: ActionableInsight,
    val timeline: Timeline,
    val rawStats: RawStats,
    val alarmInfo: AlarmInfo? = null
)

/**
 * 决策因子，包含影响综合评分的三个维度
 *
 * @param courseStress 课程压力因子（0~100）
 * @param physicalState 身体状态因子（0~100）
 * @param focusCost 专注成本因子（0~100）
 */
data class DecisionFactors(
    val courseStress: Int,
    val physicalState: Int,
    val focusCost: Int
)

/**
 * 动态干预建议卡片数据
 *
 * @param show 是否展示该建议
 * @param level 建议等级："critical"、"warning" 或 "info"
 * @param title 卡片标题
 * @param message 卡片正文内容
 */
data class ActionableInsight(
    val show: Boolean,
    val level: String,
    val title: String,
    val message: String
)

/**
 * 睡眠目标与课程的时间轴视图数据
 *
 * @param targetSleepTime 目标入睡时间，格式 HH:mm
 * @param offset 相对理想入睡时间的偏移量描述
 * @param suggestedWakeUpTime 建议起床时间，格式 HH:mm
 * @param courses 明日课程列表
 */
data class Timeline(
    val targetSleepTime: String,
    val offset: String,
    val suggestedWakeUpTime: String = "",
    val courses: List<TimelineCourse> = emptyList()
)

/**
 * 时间线中的单条课程条目
 *
 * @param time 上课时间段显示文本
 * @param name 课程名称
 */
data class TimelineCourse(
    val time: String,
    val name: String
)

/**
 * 原始统计数据，用于在 UI 中展示具体数值溯源
 *
 * @param sleepDurationStr 睡眠时长的可读字符串表示
 * @param steps 今日累计步数
 * @param targetSteps 每日步数目标值
 * @param focusRate 专注率百分比
 * @param distractionMins 累计分心时长（分钟）
 */
data class RawStats(
    val sleepDurationStr: String,
    val steps: Int,
    val targetSteps: Int,
    val focusRate: Int,
    val distractionMins: Int
)

/**
 * 闹钟信息：每次 Dashboard 刷新时由决策引擎产出，UI 层据此调用系统闹钟
 *
 * @param suggestedWakeUpHour   建议起床时间 - 时（24小时制）
 * @param suggestedWakeUpMinute 建议起床时间 - 分
 * @param alarmType             闹钟类型："default"=课表推算 | "override"=用户单次特殊闹钟 | "noClass"=无课默认
 * @param alarmLabel            写入系统闹钟的标签文案，如 "学习模式 明早 07:15 起床 · 高数课前准备"
 * @param canSetAlarm           是否允许设置闹钟（false 表示时间落入禁止区间或已过期）
 * @param reason                无法设闹钟时的原因说明（canSetAlarm=false 时有值）
 */
data class AlarmInfo(
    val suggestedWakeUpHour: Int,
    val suggestedWakeUpMinute: Int,
    val alarmType: String,     // "default" | "override" | "noClass"
    val alarmLabel: String,
    val canSetAlarm: Boolean,
    val reason: String = ""    // canSetAlarm=false 时填充原因
)

/**
 * 用户起床配置：持久化于 User 实体或 SharedPreferences
 *
 * @param preClassBufferMinutes    上课前缓冲分钟数（默认45，即闹钟 = 第一节课开始 - 缓冲）
 * @param noClassWakeUpHour        无课日的默认起床时刻-时
 * @param noClassWakeUpMinute      无课日的默认起床时刻-分
 * @param oneTimeOverrideHour      单次特殊闹钟-时（设0表示不使用特殊闹钟）
 * @param oneTimeOverrideMinute    单次特殊闹钟-分
 * @param oneTimeOverrideDate      特殊闹钟的目标日期（yyyy-MM-dd），过期自动失效
 * @param alarmEnabled             总开关：是否允许 App 设置闹钟（false 则只计算不设闹钟）
 */
data class WakeUpConfiguration(
    val preClassBufferMinutes: Int = DecisionConfig.PRE_CLASS_BUFFER_MINUTES,
    val noClassWakeUpHour: Int = DecisionConfig.NO_CLASS_DEFAULT_WAKEUP_HOUR,
    val noClassWakeUpMinute: Int = DecisionConfig.NO_CLASS_DEFAULT_WAKEUP_MINUTE,
    val oneTimeOverrideHour: Int = 0,       // 0 = 未设置特殊闹钟
    val oneTimeOverrideMinute: Int = 0,
    val oneTimeOverrideDate: String = "",   // 空 = 未设置 / 已过期
    val alarmEnabled: Boolean = true
)