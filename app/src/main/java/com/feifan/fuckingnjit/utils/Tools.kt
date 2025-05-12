package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject

class Tools {
    companion object {
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
            return JSONArray.from(result)
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
            val excludedCourses  = (0 until tableData.size)
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
            val specialCourses  = (0 until tableData.size)
                .map { tableData.getJSONObject(it) }
                .filter { item ->
                    item.getString("kclbmc") == "专业选修课程" ||
                            item.getString("kclbmc") == "大学外语类课程"
                }

            // 取出专业选修课程和大学外语类课程的最高分
            val highestScoreSpecialCourses  = specialCourses
                .groupBy { it.getString("kclbmc") }
                .mapValues { (_, items) ->
                    items.maxByOrNull { it.getDouble("bfzcj") }!!
                }
                .values

            // 合并结果
            val finalCourseList  = JSONArray.from(excludedCourses + highestScoreSpecialCourses)

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
    }
}