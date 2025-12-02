package com.feifan.fuckingnjit.model

import com.alibaba.fastjson.annotation.JSONField

// 核心课程实体：代表“某一门课在某一个时间段的安排”
data class Course(
    @JSONField(name = "id") var id: String = "",
    @JSONField(name = "name") var name: String = "未知课程",
    @JSONField(name = "teacher") var teacher: String = "未安排教师",
    @JSONField(name = "room") var classroom: String = "未安排地点",

    // 时间信息
    @JSONField(name = "day") var day: Int = 0,      // 0 表示无固定时间
    @JSONField(name = "start") var startNode: Int = 0,
    @JSONField(name = "step") var step: Int = 0,
    @JSONField(name = "weeks") var weekList: List<Int> = ArrayList(),

    @JSONField(name = "source") var source: Int = 0,
    @JSONField(name = "raw_weeks") var rawWeeks: String = ""
) {
    // 辅助判断是否为有效时间课程
    fun hasTime(): Boolean = day != 0 && weekList.isNotEmpty()
    override fun toString(): String {
        return "CourseEntity(id='$id', name='$name', teacher='$teacher', classroom='$classroom', day=$day, startNode=$startNode, step=$step, weekList=$weekList, source=$source, rawWeeks='$rawWeeks')"
    }
}