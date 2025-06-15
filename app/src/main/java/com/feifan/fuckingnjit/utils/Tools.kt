package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.Model.Course


class Tools {
    companion object {
        val timeSlotsJson = """
    [
        {"index": "1", "name": "08:00-08:45"},
        {"index": "2", "name": "08:55-09:40"},
        {"index": "3", "name": "10:10-10:55"},
        {"index": "4", "name": "11:05-11:50"},
        {"index": "5", "name": "13:40-14:25"},
        {"index": "6", "name": "14:35-15:20"},
        {"index": "7", "name": "15:40-16:25"},
        {"index": "8", "name": "16:35-17:20"},
        {"index": "9", "name": "18:30-19:15"},
        {"index": "10", "name": "19:25-20:10"},
        {"index": "11", "name": "20:20-21:05"}
    ]
""".trimIndent()
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
                        "xqmmc" to element.getString("xqmmc")//学期名称
                    )
                )
            }
            return JSONArray.parseArray(JSON.toJSONString(result))
        }
//        fun processGrades(tableData: JSONArray) {
//
//
//            // 计算平均GPA
//            val averageGPA = calculateAverageGPA(finalCourseList )
//        }

        @SuppressLint("DefaultLocale")
        fun calculateAverageGPA(tableData: JSONArray): String {

            // 删除不计入GPA的课程和特殊算法课程
            val excludedCourses = (0 until tableData.size)
                .map { tableData.getJSONObject(it) }
                .filter { item ->
                    item.getString("kcgsmc") != "劳动教育" &&
                            item.getString("kcgsmc") != "跨专业选修" &&
                            item.getString("kcgsmc") != "公选" &&
                            item.getString("kcgsmc") != "劳动选修" &&
                            item.getString("kcgsmc") != "xxx" &&
                            item.getString("kclbmc") != "专业选修课程" &&
                            item.getString("kclbmc") != "大学外语类课程"
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
                val credit = item.getDouble("xf")
                val gradePoint = item.getDouble("jd")

                totalCredit += credit
                totalCreditPoint += credit * gradePoint
            }

            return (totalCreditPoint / totalCredit).let {
                String.format("%.2f", it)
            }
        }
        fun getCourseTime(courseTime: String): ArrayList<Int> {
            var courseTime = courseTime
            courseTime = courseTime.replace("第", "")
            courseTime = courseTime.replace("节", "")
            val split =
                courseTime.split(",")
            val timeArray = ArrayList<Int>()
            for (i in split.indices) {
                val split1 =
                    split[i].split("-")

                val left = split1[0].toInt()
                val right = split1[1].toInt()

                for (j in left..right) {
                    timeArray.add(j)
                }
            }
            return timeArray
        }

        fun getTimeTableData(raw: Array<List<Course>>): List<List<List<String>>> {
            val timetableData = MutableList(20) {
                MutableList(7) {
                    MutableList(11) { "" }
                }
            }
            for (i in 1 until raw.size) {
                val weekCourses = raw[i]
                for (course in weekCourses) {
                    val time = course.getTime()
                    val week = time.getWeek() // 需确保是0-based（0-19）
                    val weekday = time.getWeekday()
                    val courseTimes = time.getCourseTime()
                    val value = "${course.getName()}@${course.getClassroom()}"

                    for (slot in courseTimes) {
                        val adjustedSlot = slot - 1 // 节次转0-based索引
                        val adjustedWeekday = weekday - 1 // 星期转0-based索引

                        // 获取当前单元格内容
                        val currentCell = timetableData[week][adjustedWeekday][adjustedSlot]

                        if (currentCell.isNotEmpty() && !currentCell.contains(value)) {
                            // 非空且不重复时拼接
                            timetableData[week][adjustedWeekday][adjustedSlot] =
                                "$currentCell!$value"
                            // 同步更新第0周
                            timetableData[0][adjustedWeekday][adjustedSlot] =
                                timetableData[week][adjustedWeekday][adjustedSlot]
                        } else {
                            // 直接赋值（包括空或重复时覆盖）
                            timetableData[week][adjustedWeekday][adjustedSlot] = value
                            timetableData[0][adjustedWeekday][adjustedSlot] = value
                        }
                    }
                }
            }
            return timetableData
        }

        fun parseCourseSchedule(courseArray: JSONArray): MutableList<HashMap<String, String>> {

            val result = mutableListOf<HashMap<String, String>>()
            try {
                var i = 0

                while (i < courseArray.size) {
                    val courseStr = courseArray.getString(i)
                    if (courseStr.isNullOrEmpty()) {
                        i++
                        continue
                    }

                    // 解析当前课程信息
                    val parts = courseStr.split("@")
                    val courseName = parts[0]
                    val location = if (parts.size > 1) parts[1] else ""
                    var startIndex = i
                    var endIndex = i

                    // 检查后续连续时间段是否同一门课程
                    while (endIndex + 1 < courseArray.size &&
                        courseArray.getString(endIndex + 1) == courseStr
                    ) {
                        endIndex++
                    }

                    // 获取时间范围
                    val startTime = JSONArray.parseArray(timeSlotsJson).getJSONObject(startIndex)
                        .getString("name").split("-")[0] // 取第一节课的开始时间
                    val endTime = JSONArray.parseArray(timeSlotsJson).getJSONObject(endIndex)
                        .getString("name").split("-")[1] // 取最后一节课的结束时间

                    result.add(
                        hashMapOf(
                            "course_name" to courseName,
                            "location" to location,
                            "time" to "$startTime-$endTime"
                        )
                    )

                    i = endIndex + 1
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }

    }
}