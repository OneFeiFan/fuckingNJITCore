package com.feifan.fuckingnjit.decision

import android.content.Context
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.monitor.StepMonitorManager
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.fuckingnjit.utils.network.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.max

@Suppress("unused")
object DecisionFacade {

    /**
     * 切换当前应用运行模式
     *
     * 将用户选择的新模式持久化到本地数据库。
     *
     * @param modeStr 目标模式的枚举名称字符串
     * @return 操作结果 JSONObject
     */
    fun switchAppMode(modeStr: String): JSONObject {
        AppDataCenter.getCurrentUser()?.let { user ->
            user.currentAppMode = AppMode.fromName(modeStr)
            AppDataCenter.saveUser(user)
        }

        return NetworkStatus.Success.toJsonResult("切换成功")
    }

    /**
     * 获取仪表盘综合评估数据
     *
     * 整合课表、睡眠记录、步数、专注率等多维数据源，
     * 经决策引擎计算后返回完整的 Dashboard 展示数据。
     *
     * @param appContext 应用上下文
     * @return 包含完整仪表盘数据的 JSONObject
     */
    suspend fun getDashboardInsight(appContext: Context): JSONObject = withContext(Dispatchers.IO) {
        try {
            val mode = AppDataCenter.getCurrentUser()?.currentAppMode ?: AppMode.BALANCE_MODE

            val curriculumsObj = UserManagerImpl.getInstance().getCurriculum(appContext, false)
            val validCoursesArray = curriculumsObj.getJSONArray("validTimeCourses") ?: JSONArray()
            val allValidCourses =
                JSON.parseArray(validCoursesArray.toJSONString(), Course::class.java)
                    ?: mutableListOf()

            val currentWeek = TodayScheduleManager.getCurrentWeek()
            val tomorrow = LocalDate.now().plusDays(1)
            val targetDay = tomorrow.dayOfWeek.value

            val tomorrowCourses = allValidCourses.filter { course ->
                course.day == targetDay && course.weekList.contains(currentWeek)
            }.sortedWith { c1, c2 ->
                if (c1.startNode != c2.startNode) c1.startNode - c2.startNode else c1.name.compareTo(
                    c2.name
                )
            }

            val recentSleepRecords = AppDataCenter.getValidSleepRecordsForUI().take(7).reversed()
            val todaySteps = StepMonitorManager.currentSessionSteps
            val distractionMins = AppDataCenter.getTodayRecord().totalDistractionMins

            val todayDayOfWeek = LocalDate.now().dayOfWeek.value
            val todayCourses = allValidCourses.filter { course ->
                course.day == todayDayOfWeek && course.weekList.contains(currentWeek)
            }

            val totalClassMins = todayCourses.sumOf { it.step * DecisionConfig.BASE_FOCUS_MINUTES }
            val focusRatePercent = if (totalClassMins > 0) {
                val focusMins = max(0, totalClassMins - distractionMins)
                (focusMins * 100 / totalClassMins)
            } else {
                100
            }

            // 从 ObjectBox AppSystem 读取用户起床配置
            val wakeUpConfig = getWakeUpConfig()

            //将获取的数送往计算
            val dashboardJson = DecisionEngine().generateDashboardJson(
                mode = mode,
                tomorrowCourses = tomorrowCourses,
                recentSleepRecords = recentSleepRecords,
                todaySteps = todaySteps,
                focusRatePercent = focusRatePercent,
                distractionMins = distractionMins,
                wakeUpConfig = wakeUpConfig
            )

            return@withContext NetworkStatus.Success.toJsonResult(dashboardJson)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext NetworkStatus.UnknownError.toJsonResult()
        }
    }

    /**
     * 获取当前起床配置
     *
     * UI 层调用示例：
     * ```kotlin
     * val config = DecisionFacade.getWakeUpConfig()
     * // config.preClassBufferMinutes → 上课缓冲时间
     * // config.noClassWakeUpHour → 无课日起床时间
     * ```
     *
     * @return WakeUpConfiguration 若从未设置则返回默认配置
     */
    fun getWakeUpConfig(): WakeUpConfiguration {
        return AppDataCenter.getSystemConfig().wakeUpConfig
    }

    /**
     * 保存起床配置
     *
     * @param config 完整的 WakeUpConfiguration 对象（UI 层可先 get 再改部分字段后 save）
     * @return 操作结果 JSONObject
     */
    fun saveWakeUpConfig(config: WakeUpConfiguration): JSONObject {
        return try {
            AppDataCenter.updateSystemConfig { it.wakeUpConfig = config }
            NetworkStatus.Success.toJsonResult("起床配置已保存")
        } catch (e: Exception) {
            e.printStackTrace()
            NetworkStatus.UnknownError.toJsonResult("保存失败: ${e.message}")
        }
    }

    /**
     * 设置单次特殊闹钟（一次性覆盖默认闹钟）
     *
     * 典型场景：明天要赶车/考试/面试，临时设一个 06:30 的特殊起床时间
     * 特殊闹钟仅对指定日期生效，过期自动失效
     *
     * @param hour      小时（0~23）
     * @param minute    分钟（0~59）
     * @param targetDate 目标日期字符串（格式 yyyy-MM-dd），通常传明天的日期；传 null 则默认为明天
     * @return 操作结果 JSONObject
     */
    fun setOneTimeOverride(hour: Int, minute: Int, targetDate: String? = null): JSONObject {
        return try {
            val currentConfig = getWakeUpConfig()
            val resolvedDate = targetDate ?: LocalDate.now().plusDays(1).toString()
            val overrideConfig = currentConfig.copy(
                oneTimeOverrideHour = hour.coerceIn(0, 23),
                oneTimeOverrideMinute = minute.coerceIn(0, 59),
                oneTimeOverrideDate = resolvedDate
            )
            saveWakeUpConfig(overrideConfig)
        } catch (e: Exception) {
            e.printStackTrace()
            NetworkStatus.UnknownError.toJsonResult("设置特殊闹钟失败: ${e.message}")
        }
    }

    /**
     * 清除单次特殊闹钟（恢复使用默认课表推算）
     *
     * @return 操作结果 JSONObject
     */
    fun clearOneTimeOverride(): JSONObject {
        return try {
            val currentConfig = getWakeUpConfig()
            val clearedConfig = currentConfig.copy(
                oneTimeOverrideHour = 0,
                oneTimeOverrideMinute = 0,
                oneTimeOverrideDate = ""
            )
            saveWakeUpConfig(clearedConfig)
        } catch (e: Exception) {
            e.printStackTrace()
            NetworkStatus.UnknownError.toJsonResult("清除特殊闹钟失败: ${e.message}")
        }
    }

    /**
     * 请求设置系统闹钟
     *
     * 此方法会：
     *   1. 根据最新数据和当前配置重新跑一遍决策引擎
     *   2. 产出最新的 AlarmInfo
     *   3. 通过 AlarmHelper 打开系统闹钟 App
     *
     * UI 层可在"设闹钟"按钮点击时直接调用此方法。
     * 如果需要先展示预览再让用户确认，可先调 getAlarmStatus() 拿信息展示，再调此方法执行。
     *
     * @param appContext Context（建议 Activity Context）
     * @return JSONObject 含 success/error 状态和 alarmInfo 信息
     */
    suspend fun requestSetAlarm(appContext: Context): JSONObject = withContext(Dispatchers.IO) {
        return@withContext try {
            // 复用 Dashboard 的完整计算链路拿到最新的 alarmInfo
            val dashboardObj = getDashboardInsight(appContext)
            val dataObj = dashboardObj.getJSONObject("data")
                ?: return@withContext NetworkStatus.NotFound.toJsonResult()

            val alarmInfoJson = dataObj.getJSONObject("alarmInfo")
            if (alarmInfoJson == null) {
                return@withContext NetworkStatus.UnknownError.toJsonResult("无法获取闹钟信息")
            }

            val alarmInfo = JSON.parseObject(alarmInfoJson.toJSONString(), AlarmInfo::class.java)
                ?: return@withContext NetworkStatus.UnknownError.toJsonResult("闹钟信息解析失败")

            // 切回主线程启动 Activity（startActivity 必须在主线程）
            val result = if (AlarmHelper.setSystemAlarm(appContext, alarmInfo)) {
                NetworkStatus.Success.toJsonResult(alarmInfoJson)
            } else {
                NetworkStatus.UnknownError.toJsonResult(alarmInfo.reason.ifEmpty { "无法设置闹钟" })
            }

            result
        } catch (e: Exception) {
            e.printStackTrace()
            NetworkStatus.UnknownError.toJsonResult("设闹钟异常: ${e.message}")
        }
    }

    /**
     * 获取当前闹钟状态（不实际设置，只返回预览信息）
     *
     * UI 层可用此方法展示"建议明天 07:15 起床，是否设置闹钟？"的确认卡片，
     * 用户确认后再调 requestSetAlarm() 执行实际操作
     *
     * 返回的 JSONObject 结构：
     * ```json
     * {
     *   "status": "success",
     *   "data": {
     *     "suggestedWakeUpHour": 7,
     *     "suggestedWakeUpMinute": 15,
     *     "alarmType": "default",
     *     "alarmLabel": "[劳逸结合模式] 明早 07:15 起床 · 高等数学 课前准备",
     *     "canSetAlarm": true,
     *     "reason": ""
     *   }
     * }
     * ```
     */
    suspend fun getAlarmStatus(appContext: Context): JSONObject = withContext(Dispatchers.IO) {
        return@withContext try {
            val dashboardObj = getDashboardInsight(appContext)
            val dataObj = dashboardObj.getJSONObject("data")
                ?: return@withContext NetworkStatus.NotFound.toJsonResult()

            val alarmInfoJson = dataObj.getJSONObject("alarmInfo")
            if (alarmInfoJson != null) {
                NetworkStatus.Success.toJsonResult(alarmInfoJson)
            } else {
                NetworkStatus.UnknownError.toJsonResult("闹钟信息不可用")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            NetworkStatus.UnknownError.toJsonResult(e.message)
        }
    }
}