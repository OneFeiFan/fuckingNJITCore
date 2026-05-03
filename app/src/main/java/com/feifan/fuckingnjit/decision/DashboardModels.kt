package com.feifan.fuckingnjit.decision

// 封装了当日综合评估结果，供前端 Dashboard UI 渲染使用
data class DashboardResponse(
    val currentMode: String,// 当前运行模式标识字符串
    val overallScore: Int, // 综合评分
    val factors: DecisionFactors,// 包含三大核心决策因子
    val actionableInsight: ActionableInsight,// 动态干预建议
    val timeline: Timeline,// 睡眠目标与今日课程的时间轴视图数据
    val rawStats: RawStats,// 原始统计数据溯源
    val alarmInfo: AlarmInfo? = null // 闹钟信息（新增：供UI层调用系统闹钟）
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
    val offset: String, // 相对基准时间的偏移描述
    val suggestedWakeUpTime: String = "",// 新增：建议起床时间，格式 HH:mm（由引擎根据课表/用户配置推算）
    val courses: List<TimelineCourse> = emptyList() // 明日课程列表
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

/**
 * 闹钟信息：每次 Dashboard 刷新时由决策引擎产出，UI 层据此调用系统闹钟
 *
 * @param suggestedWakeUpHour   建议起床时间 - 时（24小时制）
 * @param suggestedWakeUpMinute 建议起床时间 - 分
 * @param alarmType             闹钟类型："default"=课表推算 | "override"=用户单次特殊闹钟 | "noClass"=无课默认
 * @param alarmLabel            写入系统闹钟的标签文案，如 "[学霸模式] 明早 07:15 起床 · 高数课前准备"
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
 * @param defaultWakeUpHour        默认起床时刻-时（仅在无课时使用此值的 fallback 语义）
 * @param defaultWakeUpMinute      默认起床时刻-分
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