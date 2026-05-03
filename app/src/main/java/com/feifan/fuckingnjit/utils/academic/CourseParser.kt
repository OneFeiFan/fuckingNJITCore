package com.feifan.fuckingnjit.utils.academic

import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course

/**
 * 教务系统原始课程数据解析器
 *
 * 将教务接口返回的 JSON 原始数据转换为标准的 [Course] 实体列表。
 * 处理教师姓名清洗、时间段正则解析、周次字符串展开等复杂逻辑。
 */
object CourseParser {
    /** 中文星期到数字的映射表，用于将"星期三"转换为 day=3 */
    private val CN_NUM_MAP = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7
    )

    /**
     * 将教务系统接口返回的单条课程 JSON 解析为 [Course] 实体列表。
     *
     * 一条原始数据可能包含多个时间段（如"周一1-2节;周三3-4节"），
     * 因此返回值可能是包含多个 Course 的列表。无时间信息的课程（毕设/网课）
     * 会生成一个 day=0、weekList 为空的占位实体。
     *
     * @param item 教务接口返回的原始 JSON 对象，至少包含 kcmc / jxb_id 等字段
     * @return 解析后的课程列表，时间格式无法识别的段会被静默跳过
     */
    fun parseSystemItem(item: JSONObject): List<Course> {
        val list = ArrayList<Course>()

        // 提取课程基础字段，空值时使用默认值兜底
        val name = item.getString("kcmc").takeIf { !it.isNullOrBlank() } ?: "未知课程"
        val uuid = item.getString("jxb_id") ?: item.getString("kch_id") ?: ""

        val rawTeacher = item.getString("jsxx")
        val teacherName = parseTeacher(rawTeacher)

        val timeStr = item.getString("sksj")
        val roomStr =
            item.getString("jxdd").takeIf { !it.isNullOrBlank() } ?: "未安排地点"

        // 无时间信息的课程（毕设、网课等），生成 day=0 占位实体
        if (timeStr.isNullOrBlank()) {
            // 创建一个“无时间”的课程实体
            val noTimeCourse = Course(
                id = uuid,
                name = name,
                teacher = teacherName,
                classroom = roomStr,
                day = 0,
                weekList = emptyList()
            )
            list.add(noTimeCourse)
            return list
        }

        // 正常课程：按分号拆分为多个时间段，逐段解析
        val timeSegments = timeStr.split(";")
        // 地点也可能有多个段，与时间段按索引一一对应
        val roomSegments = roomStr.split(";")

        for (i in timeSegments.indices) {
            val segment = timeSegments[i]
            if (segment.isBlank()) continue

            // 地点与时间按索引配对，对应位置为空时回退到最后一个地点
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

    /**
     * 清洗教务系统原始教师名字字符串，提取纯姓名。
     *
     * 支持两种格式：
     * - 标准格式：`工号/姓名/职称`（如 `02228/庄严/讲师`），取中间字段
     * - 非标准格式：直接为纯姓名或其他文本
     *
     * 多位教师以分号分隔，最终用逗号连接返回。
     * 无效数据（`0`、`无`）会被过滤，清洗失败时返回"未安排教师"。
     *
     * @param raw 原始教师信息字符串，可能为 null
     * @return 清洗后的教师姓名
     */
    private fun parseTeacher(raw: String?): String {
        if (raw.isNullOrBlank()) return "未安排教师"

        // 按分号拆分多位教师，每位独立解析
        val teachers = raw.split(";")
        val cleanNames = ArrayList<String>()

        for (t in teachers) {
            if (t.isBlank()) continue
            // 标准格式：工号/姓名/职称，取中间的姓名字段
            val parts = t.split("/")
            if (parts.size >= 2) {
                cleanNames.add(parts[1])
            } else {
                // 非标准格式：过滤 "0" 和 "无" 等无效值后直接使用
                if (parts[0] != "0" && parts[0] != "无") {
                    cleanNames.add(parts[0])
                }
            }
        }

        if (cleanNames.isEmpty()) return "未安排教师"
        return cleanNames.joinToString(",")
    }

    /**
     * 用正则表达式解析单段上课时间文本，构建部分填充的 Course 对象。
     *
     * 匹配格式：`星期X第M-N节{周次描述}`，例如：
     * - `星期一第1-2节{1-16周}` → day=1, start=1, step=2, weekList=[1..16]
     * - `星期三第3节{5,7,9周}` → day=3, start=3, step=1, weekList=[5,7,9]
     *
     * 解析失败（格式不匹配或数字转换异常）时返回 null，由调用方静默跳过。
     *
     * @param raw 原始时间字符串，如 "星期一第1-2节{1-16周}"
     * @return 部分填充的 Course 实体（不含 name/teacher/classroom），解析失败返回 null
     */
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

    /**
     * 将教务系统的周次描述字符串展开为具体的周数列表。
     *
     * 支持的格式示例与展开结果：
     * | 输入 | 输出 |
     * |---|---|
     * | `1-3,5,7-9(单)周` | [1, 2, 3, 5, 7, 9] |
     * | `1-16周` | [1, 2, ..., 16] |
     * | `2-8(双)周` | [2, 4, 6, 8] |
     * | `5,10周` | [5, 10] |
     *
     * 解析过程中会自动去除"周"字、处理中文括号/英文括号混用，
     * 任何段解析失败时静默跳过，整体异常时返回空列表。
     *
     * @param raw 原始周次字符串（花括号内的部分），如 "1-16周" 或 "1-3,5,7-9(单)周"
     * @return 展开后的周数列表，按升序排列
     */
    private fun parseWeekString(raw: String): List<Int> {
        return try {
            val result = ArrayList<Int>()
            val cleanRaw = raw.replace("周", "")
            val parts = cleanRaw.split(",")
            for (part in parts) {
                // 范围格式：如 "1-16"、"7-9(单)"、"2-8(双)"
                if (part.contains("-")) {
                    val rangeParts = part.split("-")
                    val start = rangeParts[0].toInt()
                    val endStr = rangeParts[1]
                    var end: Int
                    var type = 0 // 0=每周, 1=单周, 2=双周
                    if (endStr.contains("单")) {
                        end = endStr.replace(Regex("[()（）单]"), "").toInt()
                        type = 1
                    } else if (endStr.contains("双")) {
                        end = endStr.replace(Regex("[()（）双]"), "").toInt()
                        type = 2
                    } else {
                        end = endStr.toInt()
                    }
                    // 按 type 过滤：正常全量、单周取奇数、双周取偶数
                    for (w in start..end) {
                        if (type == 0) result.add(w)
                        else if (type == 1 && w % 2 != 0) result.add(w)
                        else if (type == 2 && w % 2 == 0) result.add(w)
                    }
                } else {
                    // 单个周数：直接解析为整数
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