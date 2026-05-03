package com.feifan.fuckingnjit.decision

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.model.DailyRecord
import com.feifan.fuckingnjit.utils.EduScheduleConfig
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class DecisionEngine {

    /**
     * 根据明日课表与用户配置，解析目标起床时间
     *
     * 优先级：
     *   1. 特殊闹钟（oneTimeOverride）：用户单次设定的起床时间，仅当日有效，可覆盖一切
     *   2. 课表推算：明天第一节课开始时间 - 上课前缓冲分钟数
     *   3. 无课默认：用户配置的无课日起床时间（默认 09:00）
     *
     * @return WakeUpResolution 包含起床时间(分钟)、闹钟类型、第一节课开始时间(如有)
     */
    fun resolveWakeUpTime(
        tomorrowCourses: List<Course>,
        config: WakeUpConfiguration,
        tomorrowDate: LocalDate = LocalDate.now().plusDays(1)
    ): WakeUpResolution {

        // 特殊闹钟（单次覆盖，最高优先级）
        if (config.oneTimeOverrideHour > 0 && config.oneTimeOverrideDate.isNotEmpty()) {
            try {
                val overrideDate = LocalDate.parse(config.oneTimeOverrideDate)
                if (overrideDate == tomorrowDate) {
                    val overrideMinutes =
                        config.oneTimeOverrideHour * 60 + config.oneTimeOverrideMinute
                    return WakeUpResolution(
                        wakeUpMinutes = overrideMinutes,
                        alarmType = "override",
                        firstCourseStartMinutes = null
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 日期格式异常，忽略特殊闹钟
            }
        }

        // 基于课表推算
        if (tomorrowCourses.isNotEmpty()) {
            // 取最早的一节课作为锚点
            val earliestCourse = tomorrowCourses.minByOrNull { it.startNode } ?: run {
                return resolveNoClassFallback(config)
            }
            // 将节次转换为物理时间（分钟数，从 00:00 算起）
            val firstCourseStart = run {
                val startTime = EduScheduleConfig.getCourseStartTime(earliestCourse.startNode)
                startTime.hour * 60 + startTime.minute
            }

            val buffer =
                config.preClassBufferMinutes.coerceAtLeast(DecisionConfig.PRE_CLASS_BUFFER_MINUTES)
            var wakeUp = firstCourseStart - buffer

            // 安全下限保护：不能推到前一天太晚（不早于 05:00），不晚于最晚允许值
            wakeUp = wakeUp.coerceIn(6 * 60, DecisionConfig.MAX_ALLOWABLE_WAKEUP_HOUR * 60)

            return WakeUpResolution(
                wakeUpMinutes = wakeUp,
                alarmType = "default",
                firstCourseStartMinutes = firstCourseStart
            )
        }

        // 无课时使用兜底默认配置
        return resolveNoClassFallback(config)
    }

    /**
     * 无课时的默认起床时间
     */
    private fun resolveNoClassFallback(config: WakeUpConfiguration): WakeUpResolution {
        val wakeUp = config.noClassWakeUpHour * 60 + config.noClassWakeUpMinute
        return WakeUpResolution(
            wakeUpMinutes = wakeUp.coerceIn(5 * 60, DecisionConfig.MAX_ALLOWABLE_WAKEUP_HOUR * 60),
            alarmType = "noClass",
            firstCourseStartMinutes = null
        )
    }

    /**
     * 计算课程压力因子 (C_stress) [范围 0.0 ~ 1.0]
     */
    private fun calculateCourseStressFactor(tomorrowCourses: List<Course>): Float {
        if (tomorrowCourses.isEmpty()) return 0.0f

        var stressScore = 0.0f

        // 判断是否有早八
        val hasMorningClass = tomorrowCourses.any { it.startNode == 1 }
        if (hasMorningClass) {
            stressScore += DecisionConfig.STRESS_WEIGHT_MORNING_CLASS
        }

        // 明天排课数量 >= 4 大节则视为满课
        if (tomorrowCourses.size >= 4) {
            stressScore += DecisionConfig.STRESS_WEIGHT_FULL_DAY
        }

        return min(1.0f, stressScore) // 封顶 1.0
    }

    /**
     * 计算目标入睡时间
     *
     * 模型：最晚入睡时间 = 起床时间 - 理想睡眠时长(8h) - 因子惩罚量
     *
     * 因子惩罚量的四个分量（沿用原有算法，语义不变）：
     *   - 睡眠债务因子：缺觉越严重 → 惩罚越大 → 越早睡
     *   - 体能消耗因子：运动量越大 → 惩罚越大 → 越早睡
     *   - 课业压力因子：明天压力越大 → 惩罚越大 → 越早睡
     *   - 模式权重融合：根据当前模式调整健康/学习权重的占比
     *
     * @param wakeUpResolution 起床时间解析结果（由 resolveWakeUpTime 产出）
     * @param mode             当前运行模式
     * @param tomorrowCourses  明日课程列表
     * @param recentSleepRecords 近期睡眠记录
     * @param todaySteps       今日累计步数
     * @return 目标入睡时间（当天分钟数，0~1439）
     */
    fun calculateTargetSleepTime(
        wakeUpResolution: WakeUpResolution,
        mode: AppMode,
        tomorrowCourses: List<Course>,
        recentSleepRecords: List<DailyRecord>,
        todaySteps: Int
    ): Int {

        val wakeUpMins = wakeUpResolution.wakeUpMinutes

        // 睡眠债务因子 [0.0 ~ 1.0]
        val lastNightMins = recentSleepRecords.lastOrNull()?.totalSleepMinutes ?: 0
        val idealSleepMins = DecisionConfig.BASE_SLEEP_HOURS * 60
        val severeLackMins = DecisionConfig.SEVERE_SLEEP_LACK_HOURS * 60
        val sleepDebtFactor =
            max(0f, min(1f, (idealSleepMins - lastNightMins) / (idealSleepMins - severeLackMins)))
        val sleepDebtPenalty = -(sleepDebtFactor * 60f) // 缺觉→负向偏移→提前睡

        // 体能消耗因子 [0.0 ~ 1.0]
        val fatigueRatio = min(1f, todaySteps / DecisionConfig.BASE_STEPS.toFloat())
        val neutralRatio = DecisionConfig.SEDENTARY_STEPS.toFloat() / DecisionConfig.BASE_STEPS
        val fatiguePenalty = if (fatigueRatio <= neutralRatio) {
            0f
        } else {
            -((fatigueRatio - neutralRatio) / (1f - neutralRatio)) * DecisionConfig.FATIGUE_MAX_OFFSET_MINUTES
        }

        // 课业压力因子 [0.0 ~ 1.0]
        val stressFactor = calculateCourseStressFactor(tomorrowCourses)
        val stressPenalty = -(stressFactor * 45f)

        // 模式权重融合
        val finalHealthPenalty = (sleepDebtPenalty + fatiguePenalty) * mode.weight.healthWeight
        val finalStudyPenalty = stressPenalty * mode.weight.studyWeight

        var totalPenalty = (finalHealthPenalty + finalStudyPenalty).toInt()

        // 防震荡平滑：限制单次最大干预幅度
        totalPenalty = totalPenalty.coerceIn(-60, 30)

        // 理想入睡 = 起床时间 - 8小时理想睡眠
        // 实际入睡 = 理想入睡 + 惩罚量（负值=比理想时间更早）
        var targetSleepMins =
            wakeUpMins - DecisionConfig.IDEAL_SLEEP_DURATION_MINUTES + totalPenalty

        // 处理跨天：结果可能为负数（表示前一天晚上）
        if (targetSleepMins < 0) {
            targetSleepMins += 1440
        }

        return (targetSleepMins.coerceIn(0f, 1439f)).toInt()
    }

    /**
     * 根据 WakeUpResolution 构建闹钟信息供 UI 层调用
     *
     * @param wakeUpResolution 起床时间解析结果
     * @param mode             当前运行模式
     * @param config           用户起床配置
     * @param tomorrowCourses  明日课程列表（用于生成动态标签文案）
     * @return AlarmInfo 闹钟信息，UI 层据此调用系统闹钟
     */
    fun buildAlarmInfo(
        wakeUpResolution: WakeUpResolution,
        mode: AppMode,
        config: WakeUpConfiguration,
        tomorrowCourses: List<Course>
    ): AlarmInfo {
        // 总开关关闭 → 不设闹钟
        if (!config.alarmEnabled) {
            return AlarmInfo(
                suggestedWakeUpHour = 0,
                suggestedWakeUpMinute = 0,
                alarmType = wakeUpResolution.alarmType,
                alarmLabel = "",
                canSetAlarm = false,
                reason = "闹钟功能已在设置中关闭"
            )
        }

        val wakeUpMins = wakeUpResolution.wakeUpMinutes
        val hour = wakeUpMins / 60
        val minute = wakeUpMins % 60

        // 有课时，闹钟不得落入 [第一节课-MIN_ALARM, 第一节课) 区间
        val firstStart = wakeUpResolution.firstCourseStartMinutes
        if (firstStart != null) {
            val minAllowed = firstStart - DecisionConfig.MIN_ALARM_INTERVAL_BEFORE_CLASS
            if (wakeUpMins in minAllowed until firstStart) {
                return AlarmInfo(
                    suggestedWakeUpHour = hour,
                    suggestedWakeUpMinute = minute,
                    alarmType = wakeUpResolution.alarmType,
                    alarmLabel = "",
                    canSetAlarm = false,
                    reason = "建议起床时间($hour:${
                        String.format(
                            Locale.ROOT,
                            "%02d",
                            minute
                        )
                    })距首节课开始不足${DecisionConfig.MIN_ALARM_INTERVAL_BEFORE_CLASS}分钟，不允许设闹钟"
                )
            }
        }

        // 动态生成闹钟标签文案
        val label = buildAlarmLabel(mode, wakeUpResolution, tomorrowCourses, hour, minute)

        return AlarmInfo(
            suggestedWakeUpHour = hour,
            suggestedWakeUpMinute = minute,
            alarmType = wakeUpResolution.alarmType,
            alarmLabel = label,
            canSetAlarm = true
        )
    }

    /**
     * 生成闹钟标签文案
     *
     * 格式规则：
     *   特殊闹钟："特殊起床提醒 HH:mm"
     *   有课默认："明早 HH:MM 起床 · {首节课名}课前准备"
     *   无课默认："明早 HH:MM 起床 · 无课日"
     */
    private fun buildAlarmLabel(
        mode: AppMode,
        resolution: WakeUpResolution,
        tomorrowCourses: List<Course>,
        hour: Int,
        minute: Int
    ): String {
        val timeStr = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

        return when (resolution.alarmType) {
            "override" -> "[${mode.modeName}] 特殊起床提醒 $timeStr"
            "default" -> {
                val firstCourse = tomorrowCourses.minByOrNull { it.startNode }
                val courseName = firstCourse?.name ?: "课程"
                "[${mode.modeName}] 明早 $timeStr 起床 · $courseName 课前准备"
            }

            else -> "[FuckingNJIT] 明早 $timeStr 起床 · 无课日"
        }
    }

    /**
     * 计算健康维度得分
     *
     * 综合考虑睡眠时长（权重60%，其中平均睡眠占70%、昨夜睡眠占30%）
     * 和运动步数（权重40%）两个因素。
     * 当昨夜睡眠低于严重缺觉阈值时返回负分作为惩罚信号。
     *
     * @param recentSleepRecords 近期睡眠记录列表（取最近7条）
     * @param steps 今日累计步数
     * @return 健康得分浮点数，可能为负值（严重缺觉时）
     */
    private fun calculatePhysicalScore(recentSleepRecords: List<DailyRecord>, steps: Int): Float {
        val stepsScore = min(steps.toFloat() / DecisionConfig.BASE_STEPS, 1.0f) * 0.4f
        if (recentSleepRecords.isEmpty()) return 0.6f + stepsScore

        val avgSleepMins = recentSleepRecords.map { it.totalSleepMinutes }.average()
        val avgSleepHours = (avgSleepMins / 60.0).toFloat()
        val lastNightSleepHours =
            (recentSleepRecords.last().totalSleepMinutes / 60.0).toFloat()

        if (lastNightSleepHours < DecisionConfig.SEVERE_SLEEP_LACK_HOURS) return -0.5f

        val bankingScore = min(avgSleepHours / DecisionConfig.BASE_SLEEP_HOURS, 1.0f) * 0.7f
        val lastNightScore = min(lastNightSleepHours / DecisionConfig.BASE_SLEEP_HOURS, 1.0f) * 0.3f
        val sleepScore = (bankingScore + lastNightScore) * 0.6f

        return sleepScore + stepsScore
    }

    /**
     * 生成仪表盘数据
     *
     * @param wakeUpConfig 用户起床配置（传null则使用默认配置）
     */
    fun generateDashboardJson(
        mode: AppMode,
        tomorrowCourses: List<Course>,
        recentSleepRecords: List<DailyRecord>,
        todaySteps: Int,
        focusRatePercent: Int,
        distractionMins: Int,
        wakeUpConfig: WakeUpConfiguration? = null
    ): JSONObject {

        val config = wakeUpConfig ?: WakeUpConfiguration()

        // 解析起床时间（三级策略）
        val tomorrowDate = LocalDate.now().plusDays(1)
        val wakeUpResolution = resolveWakeUpTime(tomorrowCourses, config, tomorrowDate)

        // 反推入睡时间
        val targetSleepMins = calculateTargetSleepTime(
            wakeUpResolution = wakeUpResolution,
            mode = mode,
            tomorrowCourses = tomorrowCourses,
            recentSleepRecords = recentSleepRecords,
            todaySteps = todaySteps
        )

        // 构建闹钟信息
        val alarmInfo = buildAlarmInfo(wakeUpResolution, mode, config, tomorrowCourses)

        // 计算 UI 表现分及压力因子
        val physicalScore = calculatePhysicalScore(recentSleepRecords, todaySteps)
        val normalizedPhysical = max(0f, physicalScore)
        val stressFactor = calculateCourseStressFactor(tomorrowCourses)

        // 综合得分 (Score)
        val overallScore =
            ((normalizedPhysical * mode.weight.healthWeight + (focusRatePercent / 100f) * mode.weight.studyWeight) * 100).toInt()
                .coerceIn(0, 100)

        // 格式化时间字符串
        val h = (targetSleepMins / 60) % 24
        val m = targetSleepMins % 60
        val timeStr = String.format(Locale.ROOT, "%02d:%02d", h, m)

        // 偏移量（相对理想入睡时间的偏差）
        val idealSleepTime =
            wakeUpResolution.wakeUpMinutes - DecisionConfig.IDEAL_SLEEP_DURATION_MINUTES
        val adjustedIdeal = if (idealSleepTime < 0) idealSleepTime + 1440 else idealSleepTime
        val offsetMinutes = targetSleepMins - adjustedIdeal
        val offsetStr = if (offsetMinutes > 0) "+${offsetMinutes}" else "$offsetMinutes"

        // 建议起床时间字符串
        val wakeUpH = wakeUpResolution.wakeUpMinutes / 60
        val wakeUpM = wakeUpResolution.wakeUpMinutes % 60
        val wakeUpStr = String.format(Locale.ROOT, "%02d:%02d", wakeUpH, wakeUpM)

        // 动态智能建议
        val lastNightMins = recentSleepRecords.lastOrNull()?.totalSleepMinutes ?: 480
        val isSedentary = todaySteps < DecisionConfig.SEDENTARY_STEPS

        // 空堂感知
        val freeSlots = TodayScheduleManager.getAvailableFreeSlots()
        val nextSlot = freeSlots.firstOrNull()

        // 动态干预决策
        val insight = if (nextSlot != null) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val slotTimeStr =
                "${nextSlot.startTime.format(formatter)}-${nextSlot.endTime.format(formatter)}"
            val isLargeGap = nextSlot.durationMinutes >= 90

            when {
                lastNightMins < 330 && isLargeGap -> ActionableInsight(
                    true, "critical", "高优干预：大段空堂补觉",
                    "昨晚严重缺觉，系统已强制前置入睡红线至 $timeStr。下一个大段空堂 ($slotTimeStr) 建议回宿舍深度休息。"
                )

                isSedentary && !isLargeGap -> ActionableInsight(
                    true, "warning", "久坐预警：碎片时间活动",
                    "今日严重缺乏活动，可能导致失眠。建议利用 $slotTimeStr 的碎片空堂去户外或走廊活动。"
                )

                mode == AppMode.SCHOLAR_MODE && isLargeGap -> ActionableInsight(
                    true, "info", "冲刺规划：图书馆时间",
                    "当前为学霸冲刺模式。下一个空堂 ($slotTimeStr) 长达 ${nextSlot.durationMinutes} 分钟，建议前往图书馆或自习室完成沉浸式学习。"
                )

                mode == AppMode.HEALTH_MODE && isSedentary -> ActionableInsight(
                    true, "warning", "健康指令：户外运动",
                    "当前为健康活力模式且步数极低。建议在 $slotTimeStr 的空堂时间去操场完成运动目标。"
                )

                else -> null
            }
        } else null

        // 保底逻辑
        val finalInsight = insight ?: when {
            lastNightMins < 330 -> ActionableInsight(
                true, "critical", "高优干预：严重睡眠负债",
                "昨晚严重缺觉且今日已无可用白昼空堂，系统强制将今晚入睡红线前置至 $timeStr（基于明早 $wakeUpStr 起床倒推）"
            )

            isSedentary -> ActionableInsight(
                true, "warning", "久坐预警",
                "今日严重缺乏活动，建议在晚饭后去操场走走，保证入睡质量。"
            )

            stressFactor > 0.6f -> ActionableInsight(
                true, "warning", "高压预警",
                "明日课业压力较大，建议最晚入睡时间：$timeStr（明早 $wakeUpStr 起床）"
            )

            else -> ActionableInsight(
                true, "info", "智能建议",
                "综合今日消耗与明日排课，建议最晚入睡时间：$timeStr（明早 $wakeUpStr 起床）"
            )
        }

        // 组装 Timeline Courses
        val timelineCourses = tomorrowCourses.sortedBy { it.startNode }.map { course ->
            TimelineCourse("第 ${course.startNode} 节", course.name)
        }

        // 拼接最终响应（含新增的 alarmInfo 和 suggestedWakeUpTime）
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
            timeline = Timeline(
                targetSleepTime = timeStr,
                offset = offsetStr,
                suggestedWakeUpTime = wakeUpStr,
                courses = timelineCourses
            ),
            rawStats = RawStats(
                "${sleepH}h ${sleepM}m",
                todaySteps,
                DecisionConfig.BASE_STEPS,
                focusRatePercent,
                distractionMins
            ),
            alarmInfo = alarmInfo
        )

        return JSON.parseObject(JSON.toJSONString(responseObj))
    }
}

/**
 * 起床时间解析结果
 * @param wakeUpMinutes          目标起床时间（当天分钟数，0~1439）
 * @param alarmType              闹钟类型标识
 * @param firstCourseStartMinutes 明天第一节课开始时间（分钟数，无课时为null）
 */
data class WakeUpResolution(
    val wakeUpMinutes: Int,
    val alarmType: String,
    val firstCourseStartMinutes: Int?
)
