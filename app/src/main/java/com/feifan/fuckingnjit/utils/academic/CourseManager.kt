package com.feifan.fuckingnjit.utils.academic

import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import java.util.UUID

class CourseManager {

    companion object {
        // 用于处理本地课表的增加/修改
        fun saveLocalCourse(course: Course): Boolean {
            val user = AppDataCenter.getCurrentUser() ?: return false
            return try {
                val localData = user.localCurriculums ?: JSONObject()

                // 确保 local_courses 节点存在
                if (!localData.containsKey("local_courses")) {
                    localData["local_courses"] = JSONObject()
                }
                val courseMap = localData.getJSONObject("local_courses")

                // 如果是新增则生成课程id，并强制标记来源为本地
                if (course.id.isEmpty()) {
                    course.id = "local_" + UUID.randomUUID().toString()
                }
                course.source = 1

                // 更新内存树并持久化到 ObjectBox
                courseMap[course.id] = course
                user.localCurriculums = localData

                AppDataCenter.saveUser(user)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        // 删除本地课程
        fun deleteLocalCourse(courseId: String): Boolean {
            val user = AppDataCenter.getCurrentUser() ?: return false
            return try {
                val localData = user.localCurriculums ?: return false

                if (localData.containsKey("local_courses")) {
                    localData.getJSONObject("local_courses").remove(courseId)
                    user.localCurriculums = localData
                    AppDataCenter.saveUser(user)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        // 获取当前用户的所有本地课程
        fun getLocalCourses(): List<Course> {
            val list = ArrayList<Course>()
            val user = AppDataCenter.getCurrentUser() ?: return list
            val localData = user.localCurriculums ?: return list
            val courseMap = localData.getJSONObject("local_courses") ?: return list

            for (key in courseMap.keys) {
                // 使用Course反序列化本地储存
                val obj = JSONObject.parseObject(
                    courseMap.getJSONObject(key).toJSONString(),
                    Course::class.java
                )
                if (obj != null) list.add(obj)
            }
            return list
        }

        // 添加屏蔽课程的方法
        // id：课程id
        // start：节次
        fun addHiddenRule(id: String, day: Int, start: Int): Boolean {
            val user = AppDataCenter.getCurrentUser() ?: return false
            return try {
                val localData = user.localCurriculums ?: JSONObject()
                // 确保屏蔽规则存在
                if (!localData.containsKey("hidden_rules_map")) {
                    localData["hidden_rules_map"] = JSONObject()
                }
                val rulesMap = localData.getJSONObject("hidden_rules_map")

                // 组合键规则：ID@星期@节次
                rulesMap["$id@$day@$start"] = 1

                user.localCurriculums = localData
                AppDataCenter.saveUser(user)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        // 获取当前用户的屏蔽规则 Map
        fun getHiddenRules(): Map<String, Any> {
            val user = AppDataCenter.getCurrentUser() ?: return emptyMap()
            val localData = user.localCurriculums ?: return emptyMap()
            return localData.getJSONObject("hidden_rules_map")?.innerMap ?: emptyMap()
        }

        // 移除屏蔽规则（恢复课程展示）
        fun removeHiddenRule(id: String, day: Int, start: Int): Boolean {
            val user = AppDataCenter.getCurrentUser() ?: return false
            return try {
                val localData = user.localCurriculums ?: return false
                if (!localData.containsKey("hidden_rules_map")) return true

                val rulesMap = localData.getJSONObject("hidden_rules_map")
                val key = "$id@$day@$start"

                if (rulesMap.containsKey(key)) {
                    rulesMap.remove(key)
                    user.localCurriculums = localData
                    AppDataCenter.saveUser(user)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}