package com.feifan.fuckingnjit.utils

import com.alibaba.fastjson.JSONArray
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.model.User
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.UserBoxUtils
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class FreeSlot(
    val startTime: LocalTime,
    val endTime: LocalTime
) {
    // 动态计算空闲分钟数，供引擎进行“碎片/大段”判定
    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()
}

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
        val id: String,
        val courseName: String,
        val classroom: String,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val startNode: Int, // 保留给小部件做冲突判断
        val step: Int
    )

    /**
     * 获取今天白天的有效空堂时间段
     * @param minGapMinutes 最小空闲时长（分钟），默认 45 分钟
     */
    fun getAvailableFreeSlots(minGapMinutes: Long = 45): List<FreeSlot> {
        val todayDay = LocalDate.now().dayOfYear
        if (cachedSlots == null || lastUpdateDay != todayDay) {
            reloadTodaySlots() // 此方法内部已存在
        }

        val slots = cachedSlots?.sortedBy { it.startTime } ?: emptyList()
        val freeSlots = mutableListOf<FreeSlot>()

        // 核心产品逻辑：限定有效活跃区间 08:00 到 17:20 (第8节课结束)
        val dayStart = LocalTime.of(8, 0)
        val dayEnd = LocalTime.of(17, 20)

        // 1. 过滤出与有效区间有交集的课程
        val activeSlots = slots.filter { it.startTime.isBefore(dayEnd) && it.endTime.isAfter(dayStart) }

        if (activeSlots.isEmpty()) {
            freeSlots.add(FreeSlot(dayStart, dayEnd))
        } else {
            // 2. 挖掘早晨第一节课前的空档
            val firstClassStart = activeSlots.first().startTime
            if (firstClassStart.isAfter(dayStart)) {
                freeSlots.add(FreeSlot(dayStart, firstClassStart))
            }

            // 3. 挖掘课与课之间的空档
            for (i in 0 until activeSlots.size - 1) {
                val currentEnd = activeSlots[i].endTime
                val nextStart = activeSlots[i + 1].startTime
                if (currentEnd.isBefore(nextStart)) {
                    freeSlots.add(FreeSlot(currentEnd, nextStart))
                }
            }

            // 4. 挖掘最后一节课到 17:20 的空档
            val lastClassEnd = activeSlots.last().endTime
            if (lastClassEnd.isBefore(dayEnd)) {
                freeSlots.add(FreeSlot(lastClassEnd, dayEnd))
            }
        }

        // 5. 过滤掉已经过去的空堂，以及长度不满足最小阈值的空堂
        val nowTime = LocalTime.now()
        return freeSlots.filter { it.endTime.isAfter(nowTime) && it.durationMinutes >= minGapMinutes }
    }

    /**
     * 【内部核心】重新加载当天的课程并转换为物理时间
     */
    private fun reloadTodaySlots() {
        val todayDay = LocalDate.now().dayOfYear

        // --- 这部分逻辑从小部件原封不动地搬过来 ---
        val startDateStr = TimeManager.getInstance().getSemesterStartDate()
        val startDateMilli =
            LocalDate.parse(startDateStr).atStartOfDay(ZoneId.systemDefault()).toInstant()
                .toEpochMilli()
        cachedCurrentWeek = TimeManager.getInstance().calculateCurrentWeek(startDateMilli)
        val todayWeekIndex = TimeManager.getInstance().todayWeekIndex()
        val targetDay = todayWeekIndex + 1

        val user = UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId()) ?: User()
        val curriculumsStr = user.curriculums.getString("validTimeCourses")
        val allCurriculumData =
            JSONArray.parseArray(curriculumsStr, Course::class.java) ?: emptyList()

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

    /**
     * 对外 API 4 (核心新增：供 AppUsageManager 使用):
     * 获取当前正在进行的具体课程。如果当前没在上课，返回 null。
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