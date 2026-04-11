package com.feifan.fuckingnjit.utils

import com.feifan.fuckingnjit.model.Course
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import com.alibaba.fastjson.JSONArray
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.UserBoxUtils
import com.feifan.fuckingnjit.model.User

/**
 * 提取的全局单例缓存管家：专门管理当天的物理上课时间
 * 供小部件 (Widget) 和无障碍服务 (AppUsageManager) 共享使用
 */
object TodayScheduleManager {

    // 缓存当天的物理时间槽位
    private var cachedSlots: List<DailyCourseSlot>? = null
    private var lastUpdateDay: Int = -1
    private var cachedCurrentWeek: Int = -1

    /**
     * 核心实体：将抽象的节次转换为绝对的物理时间段
     */
    data class DailyCourseSlot(
        val courseName: String,
        val classroom: String,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val startNode: Int, // 保留给小部件做冲突判断
        val step: Int
    )

    /**
     * 【内部核心】重新加载当天的课程并转换为物理时间
     */
    private fun reloadTodaySlots() {
        val todayDay = LocalDate.now().dayOfYear

        // --- 这部分逻辑从小部件原封不动地搬过来 ---
        val startDateStr = TimeManager.getInstance().getSemesterStartDate()
        val startDateMilli = LocalDate.parse(startDateStr).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        cachedCurrentWeek = TimeManager.getInstance().calculateCurrentWeek(startDateMilli)
        val todayWeekIndex = TimeManager.getInstance().todayWeekIndex()
        val targetDay = todayWeekIndex + 1

        val user = UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId()) ?: User()
        val curriculumsStr = user.curriculums.getString("validTimeCourses")
        val allCurriculumData = JSONArray.parseArray(curriculumsStr, Course::class.java) ?: emptyList()

        // 1. 过滤出今天的课
        val todayCourses = allCurriculumData.filter { course ->
            course.day == targetDay && course.weekList.contains(cachedCurrentWeek)
        }.sortedWith(Comparator { c1, c2 ->
            if (c1.startNode != c2.startNode) c1.startNode - c2.startNode else c1.name.compareTo(c2.name)
        })

        // 2. 转换为带有真实 LocalTime 的 Slot
        val newSlots = mutableListOf<DailyCourseSlot>()
        for (course in todayCourses) {
            // 利用你原有的 CourseTimeUtils 进行物理时间转换
            // 假设你有一个方法能拿到开始时间，如果没有，请在 Utils 里补一个
            val startTime = CourseTimeUtils.getCourseStartTime(course.startNode)
            val endTime = CourseTimeUtils.getCourseEndTime(course.startNode, course.step)

            newSlots.add(
                DailyCourseSlot(
                    courseName = course.name,
                    classroom = course.classroom,
                    startTime = startTime,
                    endTime = endTime,
                    startNode = course.startNode,
                    step = course.step
                )
            )
        }

        cachedSlots = newSlots
        lastUpdateDay = todayDay
    }

    /**
     * 对外 API 1: 强制清空缓存 (暴露给你的 Observer 监听器用)
     */
    fun clearCache() {
        cachedSlots = null
        lastUpdateDay = -1
    }

    /**
     * 对外 API 2 (供 Widget 使用): 获取今天剩余的课
     * (这代替了你原先那个会破坏性 remove 数据的 filter 方法)
     */
    fun getRemainingCoursesForWidget(): List<DailyCourseSlot> {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots()
        }

        val slots = cachedSlots ?: return emptyList()
        val nowTime = LocalTime.now()

        // 纯函数过滤，绝不修改原缓存，保证 AppUsageManager 下午还能拿到早上的数据
        return slots.filter { it.endTime.isAfter(nowTime) }
    }

    /**
     * 对外 API 3 (核心：供 AppUsageManager 使用):
     * 判断此刻是否处于上课时间
     */
    fun isCurrentlyInClass(): Boolean {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots()
        }

        val slots = cachedSlots ?: return false
        val nowTime = LocalTime.now()

        // 遍历今天的课，看当前时间是否落在某个 [startTime, endTime] 区间内
        return slots.any { slot ->
            !nowTime.isBefore(slot.startTime) && !nowTime.isAfter(slot.endTime)
        }
    }

    fun getCurrentWeek(): Int {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots()
        }
        return cachedCurrentWeek
    }
}