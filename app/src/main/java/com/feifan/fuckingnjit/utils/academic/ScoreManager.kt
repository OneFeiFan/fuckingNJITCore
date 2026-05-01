package com.feifan.fuckingnjit.utils.academic

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject

/**
 * 教务成绩相关的专属业务处理类
 * 负责解析教务系统返回的 JSON 并计算复杂绩点
 */
object ScoreManager {

    fun getScores(raw: JSONObject): JSONArray {
        val result = mutableListOf<Map<String, Any>>()
        val items: JSONArray = raw.getJSONArray("items")
        for (i in 0 until items.size) {
            val element = items.getJSONObject(i)
            result.add(
                mapOf(
                    "bfzcj" to element.getString("bfzcj"), //真实成绩
                    "kclbmc" to element.getString("kclbmc"), //课程类别
                    "kcgsmc" to if (element.containsKey("kcgsmc")) element.getString("kcgsmc") else "", //课程归属
                    "cj" to element.getString("cj"), //成绩
                    "jd" to element.getString("jd"), //绩点
                    "xf" to element.getString("xf"), //学分
                    "jsxm" to element.getString("jsxm"), //教师姓名
                    "jxb_id" to element.getString("jxb_id"), //教学班号
                    "xnm" to element.getString("xnm"), //学年
                    "xqm" to element.getString("xqm"), //学期
                    "kcmc" to element.getString("kcmc"), //课程名称
                    "xnmmc" to element.getString("xnmmc"), //学年名称
                    "xqmmc" to element.getString("xqmmc"), //学期名称
                    "ksxz" to element.getString("ksxz"), //考试性质
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

                totalCredit += credit
                totalCreditPoint += credit * gradePoint
            }

            return (totalCreditPoint / totalCredit).let {
                String.format("%.2f", it)
            }
        } catch (e: Exception) {
            // Manager.handleException(e, "calculateAverageGPA")
            return "0.00"
        }
    }
}