package com.feifan.fuckingnjit.model

import com.alibaba.fastjson.annotation.JSONField

/**
 * 基础课程实体
 *
 * 用于表示从教务系统获取的课程信息，包含时间安排、授课教师和上课地点等字段。
 */
data class Course(
    @JSONField(name = "id") var id: String = "",
    @JSONField(name = "name") var name: String = "未知课程",
    @JSONField(name = "teacher") var teacher: String = "未安排教师",
    @JSONField(name = "room") var classroom: String = "未安排地点",

    @JSONField(name = "day") var day: Int = 0,
    @JSONField(name = "start") var startNode: Int = 0,
    @JSONField(name = "step") var step: Int = 0,
    @JSONField(name = "weeks") var weekList: List<Int> = ArrayList(),

    @JSONField(name = "source") var source: Int = 0,
    @JSONField(name = "raw_weeks") var rawWeeks: String = ""
) {
    /**
     * 判断本课程是否为有效时间课程
     *
     * 有效课程需同时满足：有固定的星期几安排且周次列表不为空。
     *
     * @return true 表示该课程具有有效的排课时间信息
     */
    fun hasTime(): Boolean = day != 0 && weekList.isNotEmpty()

    override fun toString(): String {
        return "CourseEntity(id='$id', name='$name', teacher='$teacher', classroom='$classroom', day=$day, startNode=$startNode, step=$step, weekList=$weekList, source=$source, rawWeeks='$rawWeeks')"
    }
}