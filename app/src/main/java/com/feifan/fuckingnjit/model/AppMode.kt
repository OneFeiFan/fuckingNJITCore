package com.feifan.fuckingnjit.model

sealed class AppMode(
    val modeName: String,
    val studyWeight: Float,          // 学习权重（决定综合评分的侧重点）
    val healthWeight: Float,         // 健康权重
    val maxSitTimeMinutes: Int,      // 容忍的久坐时长（分钟）
    val latestSleepWeekday: String,  // 工作日最晚入睡红线
    val latestSleepHoliday: String,  // 节假日最晚入睡红线
    val suggestedWakeTime: String,   // 建议起床时间
    val strictFocusInClass: Boolean  // 上课专注度是否开启严格惩罚（弹窗/震动）
) {
    // 1. 健康活力模式（养生派）
    // 策略：绝对服从熄灯断网，保证超8小时睡眠。
    object HealthVitality : AppMode(
        modeName = "健康活力",
        studyWeight = 0.3f, healthWeight = 0.7f,
        maxSitTimeMinutes = 45,
        latestSleepWeekday = "22:30", // 10:30熄灯秒睡
        latestSleepHoliday = "23:00", // 11:00熄灯秒睡
        suggestedWakeTime = "06:50",  // 睡饱起，从容洗漱去7:30早打卡
        strictFocusInClass = false    // 课上摸鱼只做记录，不强弹窗
    )

    // 2. 劳逸结合模式（大众日常）
    // 策略：熄灯后允许玩会儿手机，但保证明天早八不困。
    object WorkLifeBalance : AppMode(
        modeName = "劳逸结合",
        studyWeight = 0.5f, healthWeight = 0.5f,
        maxSitTimeMinutes = 60,
        latestSleepWeekday = "23:15", // 熄灯后留45分钟自由冲浪时间
        latestSleepHoliday = "23:45",
        suggestedWakeTime = "07:10",  // 极限踩点去7:30早打卡
        strictFocusInClass = false    // 课后生成摸鱼报告，不当场处刑
    )

    // 3. 学霸冲刺模式（晨星派 - 核心大改）
    // 策略：晚上绝不挑灯夜战（效率低且伤身），而是利用6:00宿舍开门去空教室自习！
    object ScholarSprint : AppMode(
        modeName = "学霸冲刺",
        studyWeight = 0.8f, healthWeight = 0.2f,
        maxSitTimeMinutes = 120, // 沉浸式学习，容忍较长久坐
        latestSleepWeekday = "23:30", // 强迫入睡，为第二天早起储备体力
        latestSleepHoliday = "23:30", // 学霸放假也规律作息
        suggestedWakeTime = "06:00",  // 6:00宿舍一开门就冲去早读/自习
        strictFocusInClass = true     // 严格模式：上课玩手机直接震动警告！
    )

    // 4. 备考状态（考试周自动触发）
    // 策略：适度透支体力，为复习让路，但设定防猝死底线。
    object ExamPreparation : AppMode(
        modeName = "备考状态",
        studyWeight = 0.9f, healthWeight = 0.1f,
        maxSitTimeMinutes = 150,
        latestSleepWeekday = "00:00", // 极限熬夜到12点，不能再晚了
        latestSleepHoliday = "00:30",
        suggestedWakeTime = "06:30",  // 保证6小时最低睡眠线
        strictFocusInClass = true     // 备考期间杜绝一切上课摸鱼
    )

    // 工具方法：根据今天是否是周末，动态返回今晚的最晚睡觉时间
    fun getTargetSleepTime(isHoliday: Boolean): String {
        return if (isHoliday) latestSleepHoliday else latestSleepWeekday
    }
}