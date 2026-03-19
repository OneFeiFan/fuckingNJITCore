package com.feifan.fuckingnjit.utils

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject


class Tools {
    //    data class TimeSlot(val index: Int, val name: String)
    companion object {
//        private val timeSlots = listOf(
//            TimeSlot(1, "08:00-08:45"),
//            TimeSlot(2, "08:55-09:40"),
//            TimeSlot(3, "10:10-10:55"),
//            TimeSlot(4, "11:05-11:50"),
//            TimeSlot(5, "13:40-14:25"),
//            TimeSlot(6, "14:35-15:20"),
//            TimeSlot(7, "15:40-16:25"),
//            TimeSlot(8, "16:35-17:20"),
//            TimeSlot(9, "18:30-19:15"),
//            TimeSlot(10, "19:25-20:10"),
//            TimeSlot(11, "20:20-21:05")
//        )

//        fun getTimeSlot(index: Int): TimeSlot = timeSlots[index - 1]

//        fun getTimeRange(startIndex: Int, endIndex: Int): String {
//            val start = getTimeSlot(startIndex).name.split("-")[0]
//            val end = getTimeSlot(endIndex).name.split("-")[1]
//            return "$start-$end"
//        }


        fun getScores(raw: JSONObject): JSONArray {
            val result = mutableListOf<Map<String, Any>>()
            val items: JSONArray = raw.getJSONArray("items")
            for (i in 0 until items.size) {
                val element = items.getJSONObject(i)
                result.add(
                    mapOf(
                        "bfzcj" to element.getString("bfzcj"),//真实成绩
                        "kclbmc" to element.getString("kclbmc"),//课程类别
                        "kcgsmc" to if (element.containsKey("kcgsmc")) element.getString("kcgsmc") else "",//课程归属
                        "cj" to element.getString("cj"),//成绩
                        "jd" to element.getString("jd"),//绩点
                        "xf" to element.getString("xf"),//学分
                        "jsxm" to element.getString("jsxm"),//教师姓名
                        "jxb_id" to element.getString("jxb_id"),//教学班号
                        "xnm" to element.getString("xnm"),//学年
                        "xqm" to element.getString("xqm"),//学期
                        "kcmc" to element.getString("kcmc"),//课程名称
                        "xnmmc" to element.getString("xnmmc"),//学年名称
                        "xqmmc" to element.getString("xqmmc"),//学期名称
                        "ksxz" to element.getString("ksxz"),//考试性质
                    )
                )
            }
            return JSONArray.parseArray(JSON.toJSONString(result))
        }

        fun calculateAverageGPA(tableData: JSONArray): String {
            try {
                // 删除不计入GPA的课程和特殊算法课程
                val excludedCourses = (0 until tableData.size)
                    .map { tableData.getJSONObject(it) }
                    .filter { item ->
                        item.getString("kcgsmc") != "劳动教育" &&
                                item.getString("kcgsmc") != "跨专业选修" &&
                                item.getString("kcgsmc") != "公选" &&
                                item.getString("kcgsmc") != "劳动选修" &&
                                item.getString("kcgsmc") != "素质拓展" &&
                                item.getString("kclbmc") != "专业选修课程" &&
                                item.getString("kclbmc") != "大学外语类课程" &&
                                item.getInteger("bfzcj") >= 60 //不是挂科的
                    }

                // 过滤特殊算法课程
                val specialCourses = (0 until tableData.size)
                    .map { tableData.getJSONObject(it) }
                    .filter { item ->
                        item.getString("kclbmc") == "专业选修课程" ||
                                item.getString("kclbmc") == "大学外语类课程"
                    }

                // 取出专业选修课程和大学外语类课程的最高分
                val highestScoreSpecialCourses = specialCourses
                    .groupBy { it.getString("kclbmc") }
                    .mapValues { (_, items) ->
                        items.maxByOrNull { it.getDouble("bfzcj") }!!
                    }
                    .values

                // 合并结果

                val finalCourseList =
                    JSONArray.parseArray(JSON.toJSONString(excludedCourses + highestScoreSpecialCourses))

                var totalCredit = 0.0
                var totalCreditPoint = 0.0

                (0 until finalCourseList.size).forEach { i ->
                    val item = finalCourseList.getJSONObject(i)

                    var gradePoint = item.getDouble("jd")

                    if (item.getString("cj") == "合格" || item.getString("cj") == "通过") {
                        if (item.getString("ksxz") != "正常考试") {
                            gradePoint = 3.0
                        } else {
                            gradePoint = 3.5
                        }
                    } else if (item.getString("cj") == "优秀") {
                        gradePoint = 4.5
                    } else if (item.getString("cj") == "良好") {
                        gradePoint = 3.5
                    } else if (item.getString("cj") == "中等") {
                        gradePoint = 2.5
                    } else if (item.getString("cj") == "及格") {
                        gradePoint = 1.5
                    } else if (item.getInteger("cj") >= 95) {
                        gradePoint = 5.0
                    } else if (item.getInteger("cj") >= 90) {
                        gradePoint = 4.5
                    } else if (item.getInteger("cj") >= 85) {
                        gradePoint = 4.0
                    } else if (item.getInteger("cj") >= 80) {
                        gradePoint = 3.5
                    } else if (item.getInteger("cj") >= 75) {
                        gradePoint = 3.0
                    } else if (item.getInteger("cj") >= 70) {
                        gradePoint = 2.5
                    } else if (item.getInteger("cj") >= 65) {
                        gradePoint = 2.0
                    } else if (item.getInteger("cj") >= 60) {
                        gradePoint = 1.0
                    }

                    if (item.getString("ksxz") != "正常考试") {
                        if (gradePoint != 1.0) {
                            gradePoint -= 0.5
                        }
                    }

                    val credit = item.getDouble("xf")

                    item.getString("kcmc")

                    totalCredit += credit
                    totalCreditPoint += credit * gradePoint
                }

                return (totalCreditPoint / totalCredit).let {
                    String.format("%.2f", it)
                }
            } catch (e: Exception) {
//                Manager.handleException(e, "calculateAverageGPA")
                return "0.00"
            }
        }

//        fun getCourseTime(courseTime: String): ArrayList<Int> {
//            val timeArray = ArrayList<Int>()
//            // 1. 去掉"第"和"节"
//            val cleaned = courseTime.replace("第".toRegex(), "").replace("节".toRegex(), "")
//            // 2. 按逗号分割多个时间段
//            val segments =
//                cleaned.split(",".toRegex()).dropLastWhile { it.isEmpty() }
//            for (segment in segments) {
//                // 3. 处理每个时间段
//                if (segment.contains("-")) {
//                    // 处理范围如"1-2"
//                    val range =
//                        segment.split("-".toRegex()).dropLastWhile { it.isEmpty() }
//                    val start = range[0].trim().toInt()
//                    val end = range[1].trim().toInt()
//
//                    for (i in start..end) {
//                        if (!timeArray.contains(i)) {
//                            timeArray.add(i)
//                        }
//                    }
//                } else {
//                    // 处理单个数字如"3"
//                    val single = segment.trim().toInt()
//                    if (!timeArray.contains(single)) {
//                        timeArray.add(single)
//                        timeArray.add(single)
//                    }
//                }
//            }
//            // 4. 排序结果
//            timeArray.sort()
//            return timeArray
//        }

//        fun getTimeTableData(raw: Array<List<Course>>): List<List<List<String>>> {
//            val timetableData = MutableList(20) {
//                MutableList(7) {
//                    MutableList(11) { "" }
//                }
//            }
//            for (i in 1 until raw.size) {
//                val weekCourses = raw[i]
//                for (course in weekCourses) {
//                    val time = course.getTime()!!
//                    val week = time.week // 需确保是0-based（0-19）
//                    val weekday = time.weekday
//                    val courseTimes = time.courseTime
//                    val value = "${course.getName()}@${course.getClassroom()}"
//
//                    for (slot in courseTimes) {
//                        val adjustedSlot = slot - 1 // 节次转0-based索引
//                        val adjustedWeekday = weekday - 1 // 星期转0-based索引
//
//                        // 获取当前单元格内容
//                        val currentCell = timetableData[week][adjustedWeekday][adjustedSlot]
//
//                        if (currentCell.isNotEmpty() && !currentCell.contains(value)) {
//                            // 非空且不重复时拼接
//                            timetableData[week][adjustedWeekday][adjustedSlot] =
//                                "$currentCell!$value"
//                            // 同步更新第0周
//                            timetableData[0][adjustedWeekday][adjustedSlot] =
//                                timetableData[week][adjustedWeekday][adjustedSlot]
//                        } else {
//                            // 直接赋值（包括空或重复时覆盖）
//                            timetableData[week][adjustedWeekday][adjustedSlot] = value
//                            timetableData[0][adjustedWeekday][adjustedSlot] = value
//                        }
//                    }
//                }
//            }
//            return timetableData
//        }

//        fun parseCourseSchedule(courseArray: List<String>): MutableList<HashMap<String, String>> {
//
//            val result = mutableListOf<HashMap<String, String>>()
//            try {
//                var i = 0
//
//                while (i < courseArray.size) {
//                    val courseStr = courseArray[i]
//                    if (courseStr.isEmpty()) {
//                        i++
//                        continue
//                    }
//
//                    // 解析当前课程信息
//                    val parts = courseStr.split("@")
//                    val courseName = parts[0]
//                    val location = if (parts.size > 1) parts[1] else ""
//                    var startIndex = i
//                    var endIndex = i
//
//                    // 检查后续连续时间段是否同一门课程
//                    while (endIndex + 1 < courseArray.size &&
//                        courseArray[endIndex + 1] == courseStr
//                    ) {
//                        endIndex++
//                    }
//
//                    result.add(
//                        hashMapOf(
//                            "course_name" to courseName,
//                            "location" to location,
//                            "time" to getTimeRange(startIndex + 1, endIndex + 1)
//                        )
//                    )
//
//                    i = endIndex + 1
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//            return result
//        }

    }
}