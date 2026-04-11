package com.feifan.fuckingnjit.decision

/**
 * 智能决策全局配置与基准线设定
 * 数据支撑参考 PNAS 等学术文献
 */
object DecisionConfig {
    // === 睡眠相关基准 ===
    const val BASE_SLEEP_HOURS = 8.0f           // 满分睡眠基准
    const val SEVERE_SLEEP_LACK_HOURS = 5.5f    // 严重缺觉阈值
    const val BASE_SLEEP_TIME_MINUTES = 23 * 60 // 基准入睡时间 23:00 (按当天的分钟数计算)

    // === 运动相关基准 ===
    const val BASE_STEPS = 6000                 // 满分步数基准
    const val SEDENTARY_STEPS = 5000            // 久坐警报步数

    // === 专注相关基准 ===
    const val BASE_FOCUS_MINUTES = 45f          // 单节课基准专注时长
    const val DISTRACTION_TOLERANCE_MIN = 10f   // 摸鱼容忍阈值

    // === 课表压力权重分配 ===
    const val STRESS_WEIGHT_MORNING_CLASS = 0.4f // 早八压力值
//    const val STRESS_WEIGHT_HARD_COURSE = 0.2f   // 硬核专业课压力值(单节)
    const val STRESS_WEIGHT_FULL_DAY = 0.2f      // 满课压力值
}