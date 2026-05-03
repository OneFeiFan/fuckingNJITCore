package com.feifan.fuckingnjit.utils.academic

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.utils.academic.ScoreManager.getScores
import java.util.Locale

/**
 * 成绩管理与 GPA 计算工具
 *
 * 负责解析教务系统的原始成绩数据以及按照学校手册算法计算加权平均绩点。
 * 排除不计入课程、特殊处理选修课取最高分等规则均已实现。
 */
object ScoreManager {

    /**
     * 将教务系统返回的原始成绩 JSON 提取为结构化的成绩列表。
     *
     * 从原始数据的 items 数组中逐条提取关键字段（成绩、绩点、学分、课程名等），
     * 统一映射为新的 Map 列表后序列化为 JSONArray 返回。
     *
     * @param raw 教务接口返回的原始 JSON，必须包含 "items" 数组字段
     * @return 结构化后的成绩列表，每项包含 bfzcj/cj/jd/xf/kcmc 等 14 个字段
     */
    fun getScores(raw: JSONObject): JSONArray {
        val result = mutableListOf<Map<String, Any>>()
        val items: JSONArray = raw.getJSONArray("items")
        for (i in 0 until items.size) {
            val element = items.getJSONObject(i)
            result.add(
                mapOf(
                    "bfzcj" to element.getString("bfzcj"),     // 最终成绩（数值）
                    "kclbmc" to element.getString("kclbmc"),   // 课程类别名称
                    "kcgsmc" to if (element.containsKey("kcgsmc")) element.getString("kcgsmc") else "",  // 课程归属
                    "cj" to element.getString("cj"),           // 等级成绩（合格/优秀/分数等）
                    "jd" to element.getString("jd"),           // 绩点
                    "xf" to element.getString("xf"),           // 学分
                    "jsxm" to element.getString("jsxm"),       // 教师姓名
                    "jxb_id" to element.getString("jxb_id"),   // 教学班号
                    "xnm" to element.getString("xnm"),         // 学年代码
                    "xqm" to element.getString("xqm"),         // 学期代码
                    "kcmc" to element.getString("kcmc"),       // 课程名称
                    "xnmmc" to element.getString("xnmmc"),     // 学年名称
                    "xqmmc" to element.getString("xqmmc"),     // 学期名称
                    "ksxz" to element.getString("ksxz"),       // 考试性质
                )
            )
        }
        return JSONArray.parseArray(JSON.toJSONString(result))
    }

    /**
     * 按照南京工程学院 2025 版学生手册的绩点规则计算加权平均 GPA。
     *
     * 算法流程：
     * 1. **排除不计入课程**：劳动教育、跨专业选修、公选、劳动选修、素质拓展、挂科（<60分）的课程不参与计算
     * 2. **选修课取最高**：专业选修和大学外语类课程，按类别分组后只保留成绩最高的一门
     * 3. **合并结果**：将常规计入课程 + 选修最高分课程合并为最终列表
     * 4. **绩点映射**：按分数段查表确定单科绩点，再根据考试性质做修正（非正常考试扣 0.5）
     *
     * 绩点对照表（正常考试）：
     * | 成绩/等级 | 绩点 |
     * |---|---|
     * | ≥95 分 / 优秀 | 5.0 / 4.5 |
     * | 90~94 | 4.5 |
     * | 85~89 | 4.0 |
     * | 80~84 | 3.5 |
     * | 75~79 | 3.0 |
     * | 70~74 | 2.5 |
     * | 65~69 | 2.0 |
     * | 60~64 | 1.0 |
     * | 合格（非正考）/ 通过（非正考） | 3.0 |
     * | 合格（正考）/ 通过（正考） | 3.5 |
     * | 良好 | 3.5 |
     * | 中等 | 2.5 |
     * | 及格 | 1.5 |
     *
     * 最终 GPA = Σ(学分 × 绩点) / Σ学分，保留两位小数。
     * 计算过程异常时降级返回 "0.00"。
     *
     * @param tableData 由 [getScores] 生成的结构化成绩列表
     * @return 格式化后的 GPA 字符串，如 "3.65"
     */
    fun calculateAverageGPA(tableData: JSONArray): String {
        try {
            // 第一步：排除不计入 GPA 的课程类型与挂科记录
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

            // 第二步：筛选出需要特殊处理的选修课程（专业选修 / 大学外语类）
            val specialCourses = (0 until tableData.size)
                .map { tableData.getJSONObject(it) }
                .filter { item ->
                    item.getString("kclbmc") == "专业选修课程" ||
                            item.getString("kclbmc") == "大学外语类课程"
                }

            // 每类选修课程只取成绩最高的那一门
            val highestScoreSpecialCourses = specialCourses
                .groupBy { it.getString("kclbmc") }
                .mapValues { (_, items) ->
                    items.maxByOrNull { it.getDouble("bfzcj") }!!
                }
                .values

            // 第三步：合并常规计入课程与选修最高分课程
            val finalCourseList =
                JSONArray.parseArray(JSON.toJSONString(excludedCourses + highestScoreSpecialCourses))

            var totalCredit = 0.0
            var totalCreditPoint = 0.0
            // 第四步：逐门课程查绩点表并累加学分加权绩点
            (0 until finalCourseList.size).forEach { i ->
                val item = finalCourseList.getJSONObject(i)

                var gradePoint = item.getDouble("jd")

                if (item.getString("cj") == "合格" || item.getString("cj") == "通过") {
                    gradePoint = if (item.getString("ksxz") != "正常考试") {
                        3.0
                    } else {
                        3.5
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

                // 非正常考试（补考/缓考等）的绩点扣 0.5，但 1.0 的及格底线不再下折
                if (item.getString("ksxz") != "正常考试") {
                    if (gradePoint != 1.0) {
                        gradePoint -= 0.5
                    }
                }
                val credit = item.getDouble("xf")

                totalCredit += credit
                totalCreditPoint += credit * gradePoint
            }

            return (totalCreditPoint / totalCredit).let {
                String.format(Locale.ROOT, "%.2f", it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "0.00"
        }
    }
}