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
    fun calculateTargetSleepTime(
        mode: AppMode,
        tomorrowCourses: List<Course>,
        recentSleepRecords: List<SleepRecord>,
        todaySteps: Int
    ): Int {
        val stressFactor = calculateCourseStressFactor(tomorrowCourses)
        val physicalFactor = calculatePhysicalStateFactor(recentSleepRecords, todaySteps)

        // 基础数学公式依然保留：课业压力推迟睡觉，健康需求提前睡觉
        val studyOffset = mode.weight.studyWeight * (1f - stressFactor) * 120f
        val healthOffset = mode.weight.healthWeight * (1f - physicalFactor) * 60f
        var totalOffsetMinutes = (studyOffset - healthOffset).toInt()

        // 结合你原本的防震荡限制：最大调整幅度限制在 +/- 45 分钟内
        totalOffsetMinutes = totalOffsetMinutes.coerceIn(-45, 45)

        return DecisionConfig.BASE_SLEEP_TIME_MINUTES + totalOffsetMinutes
    }

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
        recentSleepRecords: List<SleepRecord>, // 传入近期的睡眠记录列表
        todaySteps: Int,
        focusRatePercent: Int, // 0-100的专注率
        distractionMins: Int   // 摸鱼时长
    ): JSONObject {
        // 1. 复用核心算法算出因子
        val stressFactor = calculateCourseStressFactor(tomorrowCourses)
        val physicalFactor = calculatePhysicalStateFactor(recentSleepRecords, todaySteps)

        // 处理体力因子可能为负数的情况（用于算分）
        val normalizedPhysical = max(0f, physicalFactor)

        // 2. 顶层状态
        val response = JSONObject()
        response["currentMode"] = mode.name
        response["overallScore"] = ((normalizedPhysical * mode.weight.healthWeight + (focusRatePercent / 100f) * mode.weight.studyWeight) * 100).toInt().coerceIn(0, 100)

        // 3. 核心决策因子
        val factors = JSONObject()
        factors["courseStress"] = (stressFactor * 100).toInt()
        factors["physicalState"] = (normalizedPhysical * 100).toInt()
        factors["focusCost"] = focusRatePercent
        response["factors"] = factors

        // 4. 动态智能建议
        val insight = JSONObject()
        val showInsight = physicalFactor < 0.5f || stressFactor > 0.6f
        val level = if (physicalFactor < 0.3f) "critical" else if (stressFactor > 0.6f) "warning" else "info"

        // 算出真实的入睡红线时间
        val targetSleepMins = calculateTargetSleepTime(mode, tomorrowCourses, recentSleepRecords, todaySteps)
        val h = (targetSleepMins / 60) % 24
        val m = targetSleepMins % 60
        val timeStr = String.format("%02d:%02d", h, m)

        insight["show"] = showInsight
        insight["level"] = level
        insight["title"] = if (level == "critical") "高优干预：睡眠红线" else "智能建议"
        insight["message"] = if (level == "critical") "体力严重透支，系统已将入睡红线前置至 $timeStr" else "结合明日排课压力，建议最晚入睡时间：$timeStr"
        response["actionableInsight"] = insight

        // --- 追加 Timeline 组装逻辑 ---
        val timeline = JSONObject()
        timeline["targetSleepTime"] = timeStr

        // 计算前置/后置的偏移量
        val studyOffset = mode.weight.studyWeight * (1f - stressFactor) * 120f
        val healthOffset = mode.weight.healthWeight * (1f - physicalFactor) * 60f
        val offsetMinutes = (studyOffset - healthOffset).toInt().coerceIn(-45, 45) // 限幅保护
        timeline["offset"] = if (offsetMinutes > 0) "+${offsetMinutes}" else "${offsetMinutes}"

        val coursesArr = JSONArray()
        // 按照 startNode 排序后遍历传入的真实课表
        for (course in tomorrowCourses.sortedBy { it.startNode }) {
            val cObj = JSONObject()
            cObj["time"] = "第 ${course.startNode} 节"
            cObj["name"] = course.name
            cObj["isHard"] = false // 核心专业课暂时置空
            coursesArr.add(cObj)
        }
        timeline["courses"] = coursesArr
        response["timeline"] = timeline
        // -----------------------------

        // 5. 基础溯源数据
        val raw = JSONObject()
        // 从记录中提取昨晚的睡眠时长用于展示
        println("睡眠数据：$recentSleepRecords")
        val lastNightMins = recentSleepRecords.lastOrNull()?.totalSleepMinutes ?: 390
        println("睡眠数据1：$lastNightMins")
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