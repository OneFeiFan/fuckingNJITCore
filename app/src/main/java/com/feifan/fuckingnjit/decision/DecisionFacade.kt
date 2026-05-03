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

    //切换app状态
    fun switchAppMode(modeStr: String): JSONObject {
        AppDataCenter.getCurrentUser()?.let { user ->
            user.currentAppMode = AppMode.fromName(modeStr)
            AppDataCenter.saveUser(user)
        }

        return NetworkStatus.Success.toJsonResult("切换成功")
    }

    // 获取仪表盘数据
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

            //将获取的数送往计算
            val dashboardJson = DecisionEngine().generateDashboardJson(
                mode = mode,
                tomorrowCourses = tomorrowCourses,
                recentSleepRecords = recentSleepRecords,
                todaySteps = todaySteps,
                focusRatePercent = focusRatePercent,
                distractionMins = distractionMins
            )

            return@withContext NetworkStatus.Success.toJsonResult(dashboardJson)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext NetworkStatus.UnknownError.toJsonResult()
        }
    }
}