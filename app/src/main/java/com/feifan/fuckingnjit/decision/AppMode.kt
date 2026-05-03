package com.feifan.fuckingnjit.decision

/**
 * 模式权重配置
 *
 * @param studyWeight 学习维度权重
 * @param healthWeight 健康维度权重
 */
data class ModeWeight(
    val studyWeight: Float,
    val healthWeight: Float
)

/**
 * 干预策略配置
 *
 * @param toleranceMins 单次摸鱼容忍时长（分钟）
 * @param cooldownMins 警告冷却时间（分钟）
 * @param actionLevel 干预级别：1=静默通知/关怀，2=普通警告，3=强阻断返回桌面
 */
data class InterventionConfig(
    val toleranceMins: Int,
    val cooldownMins: Int,
    val actionLevel: Int
)

/**
 * 应用运行模式枚举
 *
 * 不同模式决定了决策引擎中学习与健康因子的权重配比，
 * 以及检测到用户分心时采取的干预强度。
 *
 * @param modeName 模式显示名称
 * @param weight 学习与健康维度的权重分配
 * @param intervention 分心行为干预策略
 */
enum class AppMode(
    val modeName: String,
    val weight: ModeWeight,
    val intervention: InterventionConfig
) {
    /** 学霸冲刺模式：高学习权重、低健康权重、严格干预 */
    SCHOLAR_MODE("学霸冲刺模式", ModeWeight(1.0f, 0.2f), InterventionConfig(2, 1, 3)),

    /** 劳逸结合模式：学习与健康均衡、中等干预 */
    BALANCE_MODE("劳逸结合模式", ModeWeight(0.5f, 0.5f), InterventionConfig(10, 10, 2)),

    /** 健康活力模式：低学习权重、高健康权重、宽松干预 */
    HEALTH_MODE("健康活力模式", ModeWeight(0.1f, 1.0f), InterventionConfig(20, 30, 1));

    companion object {
        /**
         * 根据枚举名称查找对应的 AppMode
         *
         * @param name 枚举名称字符串，匹配失败时返回 [BALANCE_MODE]
         * @return 对应的 AppMode 实例
         */
        fun fromName(name: String?): AppMode {
            return AppMode.entries.find { it.name == name } ?: BALANCE_MODE
        }
    }
}