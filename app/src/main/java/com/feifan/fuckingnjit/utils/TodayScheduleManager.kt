package com.feifan.fuckingnjit.utils

import com.alibaba.fastjson.JSONArray
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课间空堂时间槽数据类
 *
 * @param startTime 空堂开始时间
 * @param endTime 空堂结束时间
 */
data class FreeSlot(
    val startTime: LocalTime,
    val endTime: LocalTime
) {
    /** 动态计算空闲分钟数，供决策引擎进行碎片/大段判定 */
    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()
}

/**
 * 单节课的时间槽数据类（已转换为物理时间）
 *
 * @param id 课程ID
 * @param courseName 课程名称
 * @param classroom 上课地点
 * @param startTime 课程开始时间
 * @param endTime 课程结束时间
 * @param startNode 开始节次
 * @param step 持续节数
 */
data class DailyCourseSlot(
    val id: String,
    val courseName: String,
    val classroom: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startNode: Int,
    val step: Int
)

/**
 * 今日课程表管理器
 *
 * 从用户缓存的课表数据中过滤出今天的课程并转换为物理时间槽，
 * 提供空堂查询、上课状态判断、当前课程定位等能力。
 * 内部按天缓存计算结果避免重复解析。
 */
object TodayScheduleManager {

    /** 缓存当天的物理时间槽位列表 */
    private var cachedSlots: List<DailyCourseSlot>? = null
    private var lastUpdateDay: Int = -1
    private var cachedCurrentWeek: Int = -1

    /**
     * 获取今天剩余的有效空堂时间段
     *
     * 有效区间为 08:00 ~ 17:20，仅返回未过期的且长度达到阈值的空堂。
     *
     * @param minGapMinutes 最小空堂时长阈值（分钟），默认 45
     * @return 按时间排序的有效空堂列表
     */
    fun getAvailableFreeSlots(minGapMinutes: Long = 45): List<FreeSlot> {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots() // 更新缓存数据
        }

        val slots = cachedSlots?.sortedBy { it.startTime } ?: emptyList()
        val freeSlots = mutableListOf<FreeSlot>()

        // 限定有效空堂区间 08:00 到 17:20 (第8节课结束)，再晚就好好休息自由支配了
        val dayStart = LocalTime.of(8, 0)
        val dayEnd = LocalTime.of(17, 20)

        // 过滤出与有效区间有交集的课程
        val activeSlots =
            slots.filter { it.startTime.isBefore(dayEnd) && it.endTime.isAfter(dayStart) }

        if (activeSlots.isEmpty()) {
            freeSlots.add(FreeSlot(dayStart, dayEnd))//全天空说是
        } else {
            // 早晨第一节课前的空档
            val firstClassStart = activeSlots.first().startTime
            if (firstClassStart.isAfter(dayStart)) {
                freeSlots.add(FreeSlot(dayStart, firstClassStart))
            }

            // 课与课之间的空档
            for (i in 0 until activeSlots.size - 1) {
                val currentEnd = activeSlots[i].endTime
                val nextStart = activeSlots[i + 1].startTime
                if (currentEnd.isBefore(nextStart)) {
                    freeSlots.add(FreeSlot(currentEnd, nextStart))
                }
            }

            // 最后一节课到 17:20 的空档
            val lastClassEnd = activeSlots.last().endTime
            if (lastClassEnd.isBefore(dayEnd)) {
                freeSlots.add(FreeSlot(lastClassEnd, dayEnd))
            }
        }

        // 过滤掉已经过去的空堂，以及长度不满足最小阈值的空堂
        val nowTime = LocalTime.now()
        return freeSlots.filter { it.endTime.isAfter(nowTime) && it.durationMinutes >= minGapMinutes }
    }

    /**
     * 重新加载并解析当天的课程数据到时间槽缓存
     */
    private fun reloadTodaySlots() {
        // 这部分逻辑从小部件原封不动地搬过来
        val startDateStr = EduScheduleConfig.getSemesterStartDate()
        val startDateMilli =
            LocalDate.parse(startDateStr).atStartOfDay(ZoneId.systemDefault()).toInstant()
                .toEpochMilli()
        cachedCurrentWeek = EduScheduleConfig.calculateCurrentWeek(startDateMilli)
        AppDataCenter.updateSystemConfig { it.currentWeek = cachedCurrentWeek }// 顺手更新当前周
        val todayWeekIndex = Tools.todayWeekIndex()
        val targetDay = todayWeekIndex + 1

        val user = AppDataCenter.getCurrentUser() ?: User()
        val curriculumsStr = user.curriculums.getString("validTimeCourses")
        val allCurriculumData =
            JSONArray.parseArray(curriculumsStr, Course::class.java) ?: emptyList()

        // 过滤出今天的课
        val todayCourses = allCurriculumData.filter { course ->
            course.day == targetDay && course.weekList.contains(cachedCurrentWeek)
        }.sortedWith(Comparator { c1, c2 ->
            if (c1.startNode != c2.startNode) c1.startNode - c2.startNode else c1.name.compareTo(c2.name)
        })

        // 转换为带有真实 LocalTime 的 Slot
        val newSlots = mutableListOf<DailyCourseSlot>()
        for (course in todayCourses) {
            val startTime = EduScheduleConfig.getCourseStartTime(course.startNode)
            val endTime = EduScheduleConfig.getCourseEndTime(course.startNode, course.step)

            newSlots.add(
                DailyCourseSlot(
                    id = course.id,
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
        lastUpdateDay = LocalDate.now().dayOfYear
    }

    /** 强制清空课程时间槽缓存（登录后调用） */
    fun clearCache() {
        cachedSlots = null
        lastUpdateDay = -1
    }

    /**
     * 获取今天尚未结束的课程列表（用于小部件展示）
     *
     * @return 未结束的课程时间槽列表，按开始时间排序
     */
    fun getRemainingCoursesForWidget(): List<DailyCourseSlot> {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots()
        }

        val slots = cachedSlots ?: return emptyList()
        val nowTime = LocalTime.now()

        return slots.filter { it.endTime.isAfter(nowTime) }
    }

    /**
     * 判断当前时刻是否处于上课时间内
     *
     * @return true 表示当前正在上某一节课
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

    /** 获取当前教学周次 */
    fun getCurrentWeek(): Int {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots()
        }
        return cachedCurrentWeek
    }

    /**
     * 获取当前正在进行中的课程
     *
     * @return 命中的课程时间槽，未在上课时返回 null
     */
    fun getCurrentClassSlot(): DailyCourseSlot? {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots()
        }

        val slots = cachedSlots ?: return null
        val nowTime = LocalTime.now()

        // 返回当前时间命中的第一节课
        return slots.firstOrNull { slot ->
            !nowTime.isBefore(slot.startTime) && !nowTime.isAfter(slot.endTime)
        }
    }
}