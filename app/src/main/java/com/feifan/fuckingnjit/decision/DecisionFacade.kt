package com.feifan.fuckingnjit.decision

import android.content.Context
import androidx.core.content.edit
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.AppMode
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.monitor.StepMonitorManager
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.utils.NetworkStatus
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.SleepRecordBoxUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

object DecisionFacade {

    fun switchAppMode(appContext: Context, mode: String): String {
        val prefs = appContext.getSharedPreferences("app_decision", Context.MODE_PRIVATE)
        prefs.edit { putString("current_mode", mode) }

        val result = JSONObject()
        result["code"] = 200
        result["msg"] = "切换成功"
        return result.toJSONString()
    }

    suspend fun getDashboardInsight(appContext: Context): JSONObject = withContext(Dispatchers.IO) {
        val result = JSONObject()
        try {
            val prefs = appContext.getSharedPreferences("app_decision", Context.MODE_PRIVATE)
            val currentModeStr = prefs.getString("current_mode", "BALANCE_MODE") ?: "BALANCE_MODE"

            val mode = when (currentModeStr) {
                "SCHOLAR_MODE" -> AppMode.SCHOLAR_MODE
                "HEALTH_MODE" -> AppMode.HEALTH_MODE
                else -> AppMode.BALANCE_MODE
            }

            val curriculumsObj = UserManagerImpl.getInstance().getCurriculum(appContext,false)
            val validCoursesArray = curriculumsObj.getJSONArray("validTimeCourses") ?: JSONArray()
            val allValidCourses = JSON.parseArray(validCoursesArray.toJSONString(), Course::class.java) ?: mutableListOf()

            val currentWeek = BaseDataBoxUtils.getBaseData().currentWeek
            val tomorrow = LocalDate.now().plusDays(1)
            val targetDay = tomorrow.dayOfWeek.value

            val tomorrowCourses = allValidCourses.filter { course ->
                course.day == targetDay && course.weekList.contains(currentWeek)
            }.sortedWith { c1, c2 ->
                if (c1.startNode != c2.startNode) c1.startNode - c2.startNode else c1.name.compareTo(c2.name)
            }

            val recentSleepRecords = SleepRecordBoxUtils.getAllRecordsForUI().take(7).reversed()
            val todaySteps = StepMonitorManager.currentSessionSteps
            val usagePrefs = appContext.getSharedPreferences("app_usage_stats", Context.MODE_PRIVATE)
            val todayKey = "distraction_${LocalDate.now()}"
            val distractionMins = usagePrefs.getInt(todayKey, 0)

            val todayDayOfWeek = LocalDate.now().dayOfWeek.value
            val todayCourses = allValidCourses.filter { course ->
                course.day == todayDayOfWeek && course.weekList.contains(currentWeek)
            }

            val totalClassMins = todayCourses.sumOf { it.step * DecisionConfig.BASE_FOCUS_MINUTES }
            val focusRatePercent = if (totalClassMins > 0) {
                val focusMins = kotlin.math.max(0, totalClassMins - distractionMins)
                (focusMins * 100 / totalClassMins)
            } else {
                100
            }

            val engine = DecisionEngine()
            val dashboardJson = engine.generateDashboardJson(
                mode = mode,
                tomorrowCourses = tomorrowCourses,
                recentSleepRecords = recentSleepRecords,
                todaySteps = todaySteps,
                focusRatePercent = focusRatePercent,
                distractionMins = distractionMins
            )

            result["data"] = dashboardJson
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext NetworkStatus.UnknownError.toJsonResult()
        }
        return@withContext NetworkStatus.Success.toJsonResult(result)
    }
}