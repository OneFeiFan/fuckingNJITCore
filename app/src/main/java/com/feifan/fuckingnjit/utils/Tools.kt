package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.Model.Course
import java.util.Collections


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
                        "xqmmc" to element.getString("xqmmc"),//学期名称
                        "ksxz" to element.getString("ksxz"),//考试性质
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

//        fun appendToPrivateFile(fileName: String, content: String) {
//            try {
//                // 构建完整路径（确保包名正确）
//                val filePath = "/data/user/0/uni.UNI2090008/files/$fileName"
//                val file = File(filePath)
//
//                // 如果文件不存在，先创建父目录
//                if (!file.parentFile.exists()) {
//                    file.parentFile.mkdirs()
//                }
//
//                // 使用 FileWriter 追加内容（true 表示追加模式）
//                FileWriter(file, true).use { writer ->
//                    writer.append(content)
//                    writer.append("\n") // 可选：换行分隔每次写入
//                }
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//        }


        @SuppressLint("DefaultLocale")
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
                                item.getString("kcgsmc") != "xxx" &&
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
                            gradePoint = 3.0;
                        } else {
                            gradePoint = 3.5;
                        }
                    } else if (item.getString("cj") == "优秀") {
                        gradePoint = 4.5;
                    } else if (item.getString("cj") == "良好") {
                        gradePoint = 3.5;
                    } else if (item.getString("cj") == "中等") {
                        gradePoint = 2.5;
                    } else if (item.getString("cj") == "及格") {
                        gradePoint = 1.5;
                    } else if (item.getInteger("cj") >= 95) {
                        gradePoint = 5.0;
                    } else if (item.getInteger("cj") >= 90) {
                        gradePoint = 4.5;
                    } else if (item.getInteger("cj") >= 85) {
                        gradePoint = 4.0;
                    } else if (item.getInteger("cj") >= 80) {
                        gradePoint = 3.5;
                    } else if (item.getInteger("cj") >= 75) {
                        gradePoint = 3.0;
                    } else if (item.getInteger("cj") >= 70) {
                        gradePoint = 2.5;
                    } else if (item.getInteger("cj") >= 65) {
                        gradePoint = 2.0;
                    } else if (item.getInteger("cj") >= 60) {
                        gradePoint = 1.0;
                    }

                    if (item.getString("ksxz") != "正常考试") {
                        if (gradePoint != 1.0) {
                            gradePoint -= 0.5;
                        }
                    }

                    val credit = item.getDouble("xf")

                    val courseName = item.getString("kcmc")

                    totalCredit += credit
                    totalCreditPoint += credit * gradePoint

//        appendToPrivateFile(
//            "result.txt",
//            "$courseName: $credit * $gradePoint = ${credit * gradePoint} ${item.getString("ksxz")}"
//        )
                }

                return (totalCreditPoint / totalCredit).let {
                    String.format("%.2f", it)
                }
            } catch (e: Exception) {
                Manager.handleException(e, "calculateAverageGPA")
                return "0.00"
            }
        }

        fun getCourseTime(courseTime: String): ArrayList<Int> {
            val timeArray = ArrayList<Int>()
            // 1. 去掉"第"和"节"
            val cleaned = courseTime.replace("第".toRegex(), "").replace("节".toRegex(), "")
            // 2. 按逗号分割多个时间段
            val segments =
                cleaned.split(",".toRegex()).dropLastWhile { it.isEmpty() }
            for (segment in segments) {
                // 3. 处理每个时间段
                if (segment.contains("-")) {
                    // 处理范围如"1-2"
                    val range =
                        segment.split("-".toRegex()).dropLastWhile { it.isEmpty() }
                    val start = range[0].trim().toInt()
                    val end = range[1].trim().toInt()

                    for (i in start..end) {
                        if (!timeArray.contains(i)) {
                            timeArray.add(i)
                        }
                    }
                } else {
                    // 处理单个数字如"3"
                    val single = segment.trim().toInt()
                    if (!timeArray.contains(single)) {
                        timeArray.add(single)
                        timeArray.add(single)
                    }
                }
            }
            // 4. 排序结果
            timeArray.sort()
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
                    val time = course.getTime()!!
                    val week = time.week // 需确保是0-based（0-19）
                    val weekday = time.weekday
                    val courseTimes = time.courseTime
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