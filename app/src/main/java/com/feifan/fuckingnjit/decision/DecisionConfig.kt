package com.feifan.fuckingnjit.decision

// 一些基础值
object DecisionConfig {
    const val BASE_SLEEP_HOURS = 8.0f           // 满分睡眠基准（小时）
    const val SEVERE_SLEEP_LACK_HOURS = 5.5f    // 严重缺觉阈值（小时）
    const val BASE_STEPS = 7000                 // 满分步数基准
    const val SEDENTARY_STEPS = 5000            // 久坐警报步数
    const val FATIGUE_MAX_OFFSET_MINUTES = 30   // 体能因子最大提前入睡幅度

    const val BASE_FOCUS_MINUTES = 45          // 单节课基准专注时长
    const val STRESS_WEIGHT_MORNING_CLASS = 0.4f // 早八压力值
    const val STRESS_WEIGHT_FULL_DAY = 0.2f      // 满课压力值
    const val IDEAL_SLEEP_DURATION_MINUTES = BASE_SLEEP_HOURS * 60   // 理想睡眠时长，用于从起床时间反推入睡时间
    const val PRE_CLASS_BUFFER_MINUTES = 60            // 上课前缓冲时间（分钟）：闹钟时间 = 第一节课开始 - 此值
    const val NO_CLASS_DEFAULT_WAKEUP_HOUR = 8         // 无课时默认起床时刻（小时）
    const val NO_CLASS_DEFAULT_WAKEUP_MINUTE = 0       // 无课时默认起床时刻（分钟）
    const val MIN_ALARM_INTERVAL_BEFORE_CLASS = 45     // 闹钟距第一节课的最小安全间隔（分钟）：防止在禁止区间设闹钟
    const val MAX_ALLOWABLE_WAKEUP_HOUR = 11            // 最晚允许起床时间（小时）：特殊闹钟的上限保护
}