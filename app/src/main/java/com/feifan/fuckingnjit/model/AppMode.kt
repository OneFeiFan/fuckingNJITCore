package com.feifan.fuckingnjit.model

// 权重矩阵
data class ModeWeight(
    val studyWeight: Float,
    val healthWeight: Float
)

data class InterventionConfig(
    val toleranceMins: Int, // 单次摸鱼容忍时长(分钟)
    val cooldownMins: Int, // 警告冷却时间(分钟)
    val actionLevel: Int // 干预级别 (1=静默通知/关怀, 2=普通警告, 3=强阻断返回桌面)
)

// 应用运行模式配置
enum class AppMode(
    val modeName: String,
    val weight: ModeWeight,
    val intervention: InterventionConfig
) {
    SCHOLAR_MODE("学霸冲刺模式", ModeWeight(1.0f, 0.2f), InterventionConfig(2, 1, 3)),
    BALANCE_MODE("劳逸结合模式", ModeWeight(0.5f, 0.5f), InterventionConfig(10, 10, 2)),
    HEALTH_MODE("健康活力模式", ModeWeight(0.1f, 1.0f), InterventionConfig(20, 30, 1));
}