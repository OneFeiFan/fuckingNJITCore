package com.feifan.fuckingnjit.decision

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.model.DailyRecord
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min


class DecisionEngine {

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

        // 3. 满课判定：假设明天排课数量 >= 4 大节则视为满课
        if (tomorrowCourses.size >= 4) {
            stressScore += DecisionConfig.STRESS_WEIGHT_FULL_DAY
        }

        return min(1.0f, stressScore) // 封顶 1.0
    }

    fun calculateTargetSleepTime(
        mode: AppMode,
        tomorrowCourses: List<Course>,
        recentSleepRecords: List<DailyRecord>,
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
    private fun calculatePhysicalScore(recentSleepRecords: List<DailyRecord>, steps: Int): Float {
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
     * 生成仪表盘数据 (已重构为强类型驱动)
     * 注意：移除了外部传入的 focusRatePercent，改为引擎根据摸鱼时长自动计算
     */
    fun generateDashboardJson(
        mode: AppMode,
        tomorrowCourses: List<Course>,
        recentSleepRecords: List<DailyRecord>,
        todaySteps: Int,
        focusRatePercent: Int,
        distractionMins: Int // 仅需传入实际摸鱼分钟数
    ): JSONObject {
        // 2. 计算 UI 表现分及压力因子
        val physicalScore = calculatePhysicalScore(recentSleepRecords, todaySteps)
        val normalizedPhysical = max(0f, physicalScore)
        val stressFactor = calculateCourseStressFactor(tomorrowCourses)

        // 综合得分 (Score)
        val overallScore =
            ((normalizedPhysical * mode.weight.healthWeight + (focusRatePercent / 100f) * mode.weight.studyWeight) * 100).toInt()
                .coerceIn(0, 100)

        // 3. 计算红线和偏移量
        val targetSleepMins =
            calculateTargetSleepTime(mode, tomorrowCourses, recentSleepRecords, todaySteps)
        val h = (targetSleepMins / 60) % 24
        val m = targetSleepMins % 60
        val timeStr = String.format("%02d:%02d", h, m)
        val offsetMinutes = targetSleepMins - DecisionConfig.BASE_SLEEP_TIME_MINUTES
        val offsetStr = if (offsetMinutes > 0) "+${offsetMinutes}" else "${offsetMinutes}"

        // 4. 动态智能建议 (Actionable Insight)
        val lastNightMins = recentSleepRecords.lastOrNull()?.totalSleepMinutes ?: 480
        val isSedentary = todaySteps < DecisionConfig.SEDENTARY_STEPS

        // --- 空堂智能感知与打标 ---
        val freeSlots = TodayScheduleManager.getAvailableFreeSlots()
        val nextSlot = freeSlots.firstOrNull() // 优先取下一个即将来临的空堂

        // --- 动态干预决策树（整合空堂） ---
        val insight = if (nextSlot != null) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val slotTimeStr =
                "${nextSlot.startTime.format(formatter)}-${nextSlot.endTime.format(formatter)}"
            val isLargeGap = nextSlot.durationMinutes >= 90 // 90分钟为大段空堂阈值

            when {
                // 优先级1：极致疲劳 + 大段空堂 -> 强制补觉
                lastNightMins < 330 && isLargeGap -> ActionableInsight(
                    true, "critical", "高优干预：大段空堂补觉",
                    "昨晚严重缺觉，系统已强制前置入睡红线至 $timeStr。下一个大段空堂 ($slotTimeStr) 建议回宿舍深度休息。"
                )
                // 优先级2：久坐状态 + 碎片空堂 -> 轻度活动
                isSedentary && !isLargeGap -> ActionableInsight(
                    true, "warning", "久坐预警：碎片时间活动",
                    "今日严重缺乏活动，可能导致失眠。建议利用 $slotTimeStr 的碎片空堂去户外或走廊活动。"
                )
                // 优先级3：学霸模式 + 大段空堂 -> 沉浸式学习
                mode == AppMode.SCHOLAR_MODE && isLargeGap -> ActionableInsight(
                    true, "info", "冲刺规划：图书馆时间",
                    "当前为学霸冲刺模式。下一个空堂 ($slotTimeStr) 长达 ${nextSlot.durationMinutes} 分钟，建议前往图书馆或自习室完成沉浸式学习。"
                )
                // 优先级4：健康模式 + 久坐 -> 强制运动
                mode == AppMode.HEALTH_MODE && isSedentary -> ActionableInsight(
                    true, "warning", "健康指令：户外运动",
                    "当前为健康活力模式且步数极低。建议在 $slotTimeStr 的空堂时间去操场完成运动目标。"
                )

                else -> null // 未匹配到特征明显的空堂干预，进入默认保底逻辑
            }
        } else null

        // --- 保底干预逻辑（无空堂时的常规判定） ---
        val finalInsight = insight ?: when {
            lastNightMins < 330 -> ActionableInsight(
                true, "critical", "高优干预：严重睡眠负债",
                "昨晚严重缺觉且今日已无可用白昼空堂，系统强制将今晚入睡红线前置至 $timeStr"
            )

            isSedentary -> ActionableInsight(
                true, "warning", "久坐预警",
                "今日严重缺乏活动，建议在晚饭后去操场走走，保证入睡质量。"
            )

            stressFactor > 0.6f -> ActionableInsight(
                true, "warning", "高压预警",
                "明日课业压力较大，建议最晚入睡时间：$timeStr"
            )

            else -> ActionableInsight(
                true, "info", "智能建议",
                "综合今日消耗与明日排课，建议最晚入睡时间：$timeStr"
            )
        }

        // 5. 组装 Timeline Courses
        val timelineCourses = tomorrowCourses.sortedBy { it.startNode }.map { course ->
            TimelineCourse("第 ${course.startNode} 节", course.name)
        }

        // 6. 实例化强类型 Response (企业级解耦)
        val sleepH = lastNightMins / 60
        val sleepM = lastNightMins % 60
        val responseObj = DashboardResponse(
            currentMode = mode.name,
            overallScore = overallScore,
            factors = DecisionFactors(
                (stressFactor * 100).toInt(),
                (normalizedPhysical * 100).toInt(),
                focusRatePercent
            ),
            actionableInsight = finalInsight,
            timeline = Timeline(timeStr, offsetStr, timelineCourses),
            rawStats = RawStats(
                "${sleepH}h ${sleepM}m",
                todaySteps,
                DecisionConfig.BASE_STEPS,
                focusRatePercent,
                distractionMins
            )
        )

        // 7. 使用 FastJSON 序列化为 JSONObject 并返回（保证不破坏与原有前端通信的接口）
        return JSON.parseObject(JSON.toJSONString(responseObj))
    }
}