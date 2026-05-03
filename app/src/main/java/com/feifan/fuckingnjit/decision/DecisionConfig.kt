package com.feifan.fuckingnjit.decision

/**
 * 决策引擎基础配置常量
 *
 * 集中管理所有决策计算中用到的阈值、基准值和边界参数，
 * 便于统一调参和维护。
 */
object DecisionConfig {
    /** 满分睡眠基准时长（小时） */
    const val BASE_SLEEP_HOURS = 8.0f

    /** 严重缺觉判定阈值（小时），低于此值触发高优干预 */
    const val SEVERE_SLEEP_LACK_HOURS = 5.5f

    /** 步数满分基准值 */
    const val BASE_STEPS = 7000

    /** 久坐警戒步数阈值，低于此值发出久坐预警 */
    const val SEDENTARY_STEPS = 5000

    /** 体能消耗因子导致的最大提前入睡幅度（分钟） */
    const val FATIGUE_MAX_OFFSET_MINUTES = 30

    /** 单节课基准专注时长（分钟） */
    const val BASE_FOCUS_MINUTES = 45

    /** 存在早八课程时的压力权重值 */
    const val STRESS_WEIGHT_MORNING_CLASS = 0.4f

    /** 排课达到满课标准时的附加压力权重值 */
    const val STRESS_WEIGHT_FULL_DAY = 0.2f

    /** 理想睡眠总时长（分钟），用于从起床时间反推目标入睡时间 */
    const val IDEAL_SLEEP_DURATION_MINUTES = BASE_SLEEP_HOURS * 60

    /** 上课前缓冲时间（分钟），闹钟时间 = 第一节课开始时间 - 此值 */
    const val PRE_CLASS_BUFFER_MINUTES = 60

    /** 无课日的默认起床时刻 - 时 */
    const val NO_CLASS_DEFAULT_WAKEUP_HOUR = 8

    /** 无课日的默认起床时刻 - 分 */
    const val NO_CLASS_DEFAULT_WAKEUP_MINUTE = 0

    /** 闹钟距第一节课的最小安全间隔（分钟），防止在禁止区间设置闹钟 */
    const val MIN_ALARM_INTERVAL_BEFORE_CLASS = 45

    /** 最晚允许起床时间（小时），特殊闹钟的上限保护 */
    const val MAX_ALLOWABLE_WAKEUP_HOUR = 11
}