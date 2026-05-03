package com.feifan.fuckingnjit.utils.academic

import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course

object CourseParser {
    private val CN_NUM_MAP = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7
    )

    // 教务系统原始数据序列化为Course对象
    fun parseSystemItem(item: JSONObject): List<Course> {
        val list = ArrayList<Course>()

        // 基础信息获取（带默认值防御）
        val name = item.getString("kcmc").takeIf { !it.isNullOrBlank() } ?: "未知课程"
        val uuid = item.getString("jxb_id") ?: item.getString("kch_id") ?: "" //课程id

        // 老师姓名清洗
        val rawTeacher = item.getString("jsxx") // 可能为null
        val teacherName = parseTeacher(rawTeacher)

        // 获取时间和地点字符串
        val timeStr = item.getString("sksj") // 可能为null
        val roomStr =
            item.getString("jxdd").takeIf { !it.isNullOrBlank() } ?: "未安排地点" // 可能为null或者没有地点

        // 情况A：如果没有时间信息（如毕设、网课）
        if (timeStr.isNullOrBlank()) {
            // 创建一个“无时间”的课程实体
            val noTimeCourse = Course(
                id = uuid,
                name = name,
                teacher = teacherName,
                classroom = roomStr,
                day = 0, // 标记无时间
                weekList = emptyList()
            )
            list.add(noTimeCourse)
            return list
        }

        // 情况B：正常课程解析
        val timeSegments = timeStr.split(";")
        // 处理地点（可能存在多个地点）
        val roomSegments = roomStr.split(";")

        for (i in timeSegments.indices) {
            val segment = timeSegments[i]
            if (segment.isBlank()) continue

            // 1. 尝试取对应 index 的地点
            // 2. 取不到则取最后一个非空地点 (没招了)
            var currentRoom = roomSegments.getOrNull(i)
            if (currentRoom.isNullOrBlank()) {
                currentRoom = roomSegments.last()
            }

            val entity = parseTimeSegment(segment)
            if (entity != null) {
                entity.id = uuid
                entity.name = name
                entity.teacher = teacherName
                entity.classroom = currentRoom
                entity.source = 0
                list.add(entity)
            }
        }
        return list
    }

    // 清洗教师名字
    private fun parseTeacher(raw: String?): String {
        if (raw.isNullOrBlank()) return "未安排教师"

        // 例子: "02228/庄严/讲师;99181/媒体外聘1/无"
        // 先按分号拆多位老师
        val teachers = raw.split(";")
        val cleanNames = ArrayList<String>()

        for (t in teachers) {
            if (t.isBlank()) continue
            // 按斜杠拆分详细信息
            val parts = t.split("/")
            if (parts.size >= 2) {
                // 标准格式：工号/姓名/职称 -> 取中间的姓名
                cleanNames.add(parts[1])
            } else {
                // 非标准格式：直接是姓名，或者其他
                if (parts[0] != "0" && parts[0] != "无") { // 过滤掉显然无效的数据
                    cleanNames.add(parts[0])
                }
            }
        }

        if (cleanNames.isEmpty()) return "未安排教师"
        return cleanNames.joinToString(",")
    }

    // 采用正则初步构建Course对象
    private fun parseTimeSegment(raw: String): Course? {
        try {
            val regex = Regex("星期(.)第(\\d+)-?(\\d*)节\\{(.+)\\}")
            val match = regex.find(raw) ?: return null

            val (dayCn, startStr, endStr, weekStr) = match.destructured

            val day = CN_NUM_MAP[dayCn] ?: 1
            val start = startStr.toInt()
            val end = if (endStr.isNotEmpty()) endStr.toInt() else start

            return Course(
                day = day,
                startNode = start,
                step = end - start + 1,
                weekList = parseWeekString(weekStr),
                rawWeeks = weekStr
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // 解析周次字符串
    // "1-3,5,7-9(单)周"
    // 输出: [1, 2, 3, 5, 7, 9]
    private fun parseWeekString(raw: String): List<Int> {
        return try {
            val result = ArrayList<Int>()
            val cleanRaw = raw.replace("周", "")// 清除无用字符
            val parts = cleanRaw.split(",")// 按,预拆分
            for (part in parts) {
                // 拆分类似1-3的格式
                if (part.contains("-")) {
                    val rangeParts = part.split("-")
                    val start = rangeParts[0].toInt()
                    val endStr = rangeParts[1]
                    var end: Int
                    var type = 0
                    if (endStr.contains("单")) {
                        end = endStr.replace(Regex("[()（）单]"), "").toInt()
                        type = 1
                    } else if (endStr.contains("双")) {
                        end = endStr.replace(Regex("[()（）双]"), "").toInt()
                        type = 2
                    } else {
                        end = endStr.toInt()
                    }
                    // 按照处理正常/单/双周数据
                    for (w in start..end) {
                        if (type == 0) result.add(w)
                        else if (type == 1 && w % 2 != 0) result.add(w)
                        else if (type == 2 && w % 2 == 0) result.add(w)
                    }
                } else {
                    // 处理单个字符或异常情况
                    val w = part.toIntOrNull()
                    if (w != null) result.add(w)
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}