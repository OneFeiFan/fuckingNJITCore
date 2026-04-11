package com.feifan.fuckingnjit.model

/**
 * 权重矩阵实体
 * @param studyWeight 学习/课表导向的权重 (W_学习)
 * @param healthWeight 健康/作息导向的权重 (W_健康)
 */
data class ModeWeight(
    val studyWeight: Float,
    val healthWeight: Float
)

/**
 * 应用运行模式配置
 */
enum class AppMode(val modeName: String, val weight: ModeWeight) {
    SCHOLAR_MODE("学霸冲刺模式", ModeWeight(1.0f, 0.2f)),
    HEALTH_MODE("健康活力模式", ModeWeight(0.1f, 1.0f)),
    BALANCE_MODE("劳逸结合模式", ModeWeight(0.5f, 0.5f));
}