package com.feifan.fuckingnjit.decision

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.AppMode
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.model.SleepRecord
import kotlin.math.max
import kotlin.math.min

class DecisionEngine {

    /**
     * 计算当天的目标入睡时间（返回从 00:00 算起的分钟数，例如 1380 代表 23:00）
     */
//    fun calculateTargetSleepTime(
//        mode: AppMode,
//        tomorrowCourses: List<Course>,
//        recentSleepRecords: List<SleepRecord>,
//        todaySteps: Int
//    ): Int {
//        val stressFactor = calculateCourseStressFactor(tomorrowCourses)
//        val physicalFactor = calculatePhysicalStateFactor(recentSleepRecords, todaySteps)
//
//        // 基础数学公式依然保留：课业压力推迟睡觉，健康需求提前睡觉
//        val studyOffset = mode.weight.studyWeight * (1f - stressFactor) * 120f
//        val healthOffset = mode.weight.healthWeight * (1f - physicalFactor) * 60f
//        var totalOffsetMinutes = (studyOffset - healthOffset).toInt()
//
//        // 结合你原本的防震荡限制：最大调整幅度限制在 +/- 45 分钟内
//        totalOffsetMinutes = totalOffsetMinutes.coerceIn(-45, 45)
//
//        return DecisionConfig.BASE_SLEEP_TIME_MINUTES + totalOffsetMinutes
//    }

    /**
     * 因子1：计算课程压力因子 (C_stress) [范围 0.0 ~ 1.0]
     */
    private fun calculateCourseStressFactor(tomorrowCourses: List<Course>): Float {
        if (tomorrowCourses.isEmpty()) return 0.0f

        println(tomorrowCourses.toString())

        var stressScore = 0.0f

        // 1. 判断是否有早八：直接利用教务系统的 startNode 属性
        // 假设 startNode 为 1 (或 2 内) 代表早八第一大节
        val hasMorningClass = tomorrowCourses.any { it.startNode == 1 || it.startNode == 2 }
        if (hasMorningClass) {
            stressScore += DecisionConfig.STRESS_WEIGHT_MORNING_CLASS
        }

        // 2. 核心专业课判断：由于接口暂无该数据，逻辑暂时置空
        // val hardCourseCount = tomorrowCourses.count { it.isHardcore }
        // stressScore += (hardCourseCount * DecisionConfig.STRESS_WEIGHT_HARD_COURSE)

        // 3. 满课判定：假设明天排课数量 >= 4 大节则视为满课
        if (tomorrowCourses.size >= 4) {
            stressScore += DecisionConfig.STRESS_WEIGHT_FULL_DAY
        }

        return min(1.0f, stressScore) // 封顶 1.0
    }

    /**
     * 2. [全新算法] 计算目标入睡时间 (生理干预逻辑)
     */
    /**
     * 计算当天的目标入睡时间（返回从 00:00 算起的分钟数，例如 1380 代表 23:00）
     */
    /**
     * [连续因子模型] 计算目标入睡时间 (生理干预逻辑)
     */
    fun calculateTargetSleepTime(
        mode: AppMode,
        tomorrowCourses: List<Course>,
        recentSleepRecords: List<SleepRecord>,
        todaySteps: Int
    ): Int {
        // 基准线：23:00 (1380分钟)
        val targetMinutes = DecisionConfig.BASE_SLEEP_TIME_MINUTES

        // 因子A：睡眠债务因子 (Sleep Debt Factor) [0.0 ~ 1.0]
        // 假设理想睡眠为 480 分钟 (8小时)
        val lastNightMins = recentSleepRecords.lastOrNull()?.totalSleepMinutes ?: 480
        // 理想睡眠 480 分钟 (8小时)，严重缺觉线 330 分钟 (5.5小时)
// 分母改为 (480 - 330)，意味着当睡眠下降到 330 分钟时，因子直接拉满到 1.0
        val sleepDebtFactor = max(0f, min(1f, (480f - lastNightMins) / (480f - 330f)))

// 此时，如果睡眠 <= 5.5 小时，sleepDebtFactor 必然是 1.0 (满载惩罚)
        val sleepDebtOffset = -(sleepDebtFactor * 60f)

        // 因子B：体能消耗因子 (Energy Expenditure Factor) [0.0 ~ 1.0]
        // 设定 10000 步为单日体能消耗极大值
        val fatigueRatio = min(1f, todaySteps / 10000f)
        // 数学映射：以 0.5 (5000步) 为生理中值轴。
        // 代入公式：(0.5 - ratio) * 60。
        // 若走 10000 步(1.0) -> -30 分钟 (极度疲劳，提前半小时睡)
        // 若走 1000 步(0.1)  -> +24 分钟 (极度久坐，往后推迟)
        val fatigueOffset = (0.5f - fatigueRatio) * 60f

        // 因子C：课业压力因子 (Stress Factor) [0.0 ~ 1.0]
        val stressFactor = calculateCourseStressFactor(tomorrowCourses)
        // 满压状态下最多提前 45 分钟
        val stressOffset = -(stressFactor * 45f)

        // 模式权重融合 (Weight Integration)
        val finalHealthOffset = (sleepDebtOffset + fatigueOffset) * mode.weight.healthWeight
        val finalStudyOffset = stressOffset * mode.weight.studyWeight

        var totalOffset = (finalHealthOffset + finalStudyOffset).toInt()

        // 最终的防震荡平滑处理：限制算法的单次最大干预幅度
        totalOffset = totalOffset.coerceIn(-60, 30)

        return targetMinutes + totalOffset
    }

    /**
     * 1. 表现得分计算 (专供 UI 进度条使用，不参与睡眠推导)
     * 逻辑不变：睡得越好、走得越多，得分越高
     */
    private fun calculatePhysicalScore(recentSleepRecords: List<SleepRecord>, steps: Int): Float {
        val stepsScore = min(steps.toFloat() / DecisionConfig.BASE_STEPS, 1.0f) * 0.4f
        if (recentSleepRecords.isEmpty()) return 0.6f + stepsScore

        val avgSleepMins = recentSleepRecords.map { it.totalSleepMinutes }.average()
        val avgSleepHours = (avgSleepMins / 60.0).toFloat()
        val lastNightSleepHours = (recentSleepRecords.last().totalSleepMinutes / 60.0).toFloat()

        if (lastNightSleepHours < DecisionConfig.SEVERE_SLEEP_LACK_HOURS) return -0.5f

        val bankingScore = min(avgSleepHours / DecisionConfig.BASE_SLEEP_HOURS, 1.0f) * 0.7f
        val lastNightScore = min(lastNightSleepHours / DecisionConfig.BASE_SLEEP_HOURS, 1.0f) * 0.3f
        val sleepScore = (bankingScore + lastNightScore) * 0.6f

        return sleepScore + stepsScore
    }

    /**
     * 因子2：计算体力状态因子 (P_state) [范围 -1.0 ~ 1.0]
     */
    private fun calculatePhysicalStateFactor(recentSleepRecords: List<SleepRecord>, steps: Int): Float {
        // 1. 步数得分 (权重 0.4)
        val stepsScore = min(steps.toFloat() / DecisionConfig.BASE_STEPS, 1.0f) * 0.4f

        if (recentSleepRecords.isEmpty()) {
            return 0.6f + stepsScore // 没数据时给个及格分
        }

        // 2. 算近期的平均睡眠时长（小时）
        val avgSleepMins = recentSleepRecords.map { it.totalSleepMinutes }.average()
        val avgSleepHours = (avgSleepMins / 60.0).toFloat()

        // 3. 昨晚的单次睡眠时长（近因效应，昨晚的影响最大）
        val lastNightSleepHours = (recentSleepRecords.last().totalSleepMinutes / 60.0).toFloat()

        // --- 科学判定逻辑 ---

        // 极端情况：哪怕你前几天睡得再好，昨晚低于严重缺觉线 (5.5h)，今天必须拉响警报
        if (lastNightSleepHours < DecisionConfig.SEVERE_SLEEP_LACK_HOURS) {
            return -0.5f
        }

        // 睡眠得分公式：70%看平均储备(避免单日剧烈震荡)，30%看昨晚(近因)
        val bankingScore = min(avgSleepHours / DecisionConfig.BASE_SLEEP_HOURS, 1.0f) * 0.7f
        val lastNightScore = min(lastNightSleepHours / DecisionConfig.BASE_SLEEP_HOURS, 1.0f) * 0.3f

        // 睡眠总分 (权重 0.6)
        val sleepScore = (bankingScore + lastNightScore) * 0.6f

        return sleepScore + stepsScore
    }

    fun generateDashboardJson(
        mode: AppMode,
        tomorrowCourses: List<Course>,
        recentSleepRecords: List<SleepRecord>,
        todaySteps: Int,
        focusRatePercent: Int,
        distractionMins: Int
    ): JSONObject {
        // 1. 计算 UI 表现分及压力因子 (完全独立于干预逻辑，专供进度条展示)
        val physicalScore = calculatePhysicalScore(recentSleepRecords, todaySteps)
        val normalizedPhysical = max(0f, physicalScore)
        val stressFactor = calculateCourseStressFactor(tomorrowCourses)

        // 2. 顶层状态组装
        val response = JSONObject()
        response["currentMode"] = mode.name
        // 综合得分依然是雷达图/进度条的总分
        response["overallScore"] = ((normalizedPhysical * mode.weight.healthWeight + (focusRatePercent / 100f) * mode.weight.studyWeight) * 100).toInt().coerceIn(0, 100)

        // 3. 核心决策因子组装
        val factors = JSONObject()
        factors["courseStress"] = (stressFactor * 100).toInt()
        factors["physicalState"] = (normalizedPhysical * 100).toInt()
        factors["focusCost"] = focusRatePercent
        response["factors"] = factors

        // 4. 调用连续因子模型算出入睡红线
        val targetSleepMins = calculateTargetSleepTime(mode, tomorrowCourses, recentSleepRecords, todaySteps)
        val h = (targetSleepMins / 60) % 24
        val m = targetSleepMins % 60
        val timeStr = String.format("%02d:%02d", h, m)

        // 5. 动态智能建议 (Actionable Insight) - 结合新的生理中值逻辑
        val insight = JSONObject()
        val lastNightMins = recentSleepRecords.lastOrNull()?.totalSleepMinutes ?: 480
        val fatigueRatio = min(1f, todaySteps / 10000f) // 体力消耗中值轴参数

        if (lastNightMins < 330) {
            insight["show"] = true
            insight["level"] = "critical"
            insight["title"] = "高优干预：严重睡眠负债"
            insight["message"] = "昨晚严重缺觉，系统已强制将今晚入睡红线前置至 $timeStr"
        } else if (fatigueRatio < 0.3f) { // 即今天步数 < 3000
            insight["show"] = true
            insight["level"] = "warning"
            insight["title"] = "久坐预警"
            insight["message"] = "今日严重缺乏活动，你可能无法在 $timeStr 前入睡，建议利用空堂去操场走走。"
        } else {
            // 普通建议状态
            insight["show"] = true
            insight["level"] = if (stressFactor > 0.6f) "warning" else "info"
            insight["title"] = "智能建议"
            insight["message"] = "综合今日消耗与明日排课，建议最晚入睡时间：$timeStr"
        }
        response["actionableInsight"] = insight

        // 6. Timeline 组装逻辑 (逆推 Offset，无需重复计算)
        val timeline = JSONObject()
        timeline["targetSleepTime"] = timeStr

        // 逆推干预偏移量：用干预后的分钟数减去 23:00 基准线 (1380分钟)
        val offsetMinutes = targetSleepMins - DecisionConfig.BASE_SLEEP_TIME_MINUTES
        timeline["offset"] = if (offsetMinutes > 0) "+${offsetMinutes}" else "${offsetMinutes}"

        val coursesArr = JSONArray()
        for (course in tomorrowCourses.sortedBy { it.startNode }) {
            val cObj = JSONObject()
            cObj["time"] = "第 ${course.startNode} 节"
            cObj["name"] = course.name
            cObj["isHard"] = false // 预留坑位
            coursesArr.add(cObj)
        }
        timeline["courses"] = coursesArr
        response["timeline"] = timeline

        // 7. 基础溯源数据 (Raw Stats)
        val raw = JSONObject()
        val sleepH = lastNightMins / 60
        val sleepM = lastNightMins % 60
        raw["sleepDurationStr"] = "${sleepH}h ${sleepM}m"
        raw["steps"] = todaySteps
        raw["targetSteps"] = DecisionConfig.BASE_STEPS
        raw["focusRate"] = focusRatePercent
        raw["distractionMins"] = distractionMins
        response["rawStats"] = raw

        return response
    }
}