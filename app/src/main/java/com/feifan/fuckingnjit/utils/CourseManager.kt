package com.feifan.fuckingnjit.utils

import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.utils.database.UserBoxUtils
import java.util.UUID

class CourseManager {

    companion object {
        // 保存课程（新增或修改）
        fun saveLocalCourse(userId: String, course: Course): Boolean {
            try {
                val user = UserBoxUtils.getUserById(userId) ?: return false
                var localData = user.localCurriculums
                if (localData == null) localData = JSONObject()

                // 1. 确保有 local_courses 节点
                if (!localData.containsKey("local_courses")) {
                    localData["local_courses"] = JSONObject()
                }
                val courseMap = localData.getJSONObject("local_courses")

                // 2. 如果是新增，生成 UUID
                if (course.id.isEmpty()) {
                    course.id = "local_" + UUID.randomUUID().toString()
                }
                course.source = 1 // 强制标记为本地
                // 3. 存入 Map
                courseMap[course.id] = course // FastJSON 会自动序列化 Bean
                // 4. 保存回 UserBox
                user.localCurriculums = localData
                UserBoxUtils.updateUser(user)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        fun deleteLocalCourse(userId: String, courseId: String): Boolean {
            return try {
                val user = UserBoxUtils.getUserById(userId) ?: return false
                val localData = user.localCurriculums ?: return false

                if (localData.containsKey("local_courses")) {
                    localData.getJSONObject("local_courses").remove(courseId)
                    user.localCurriculums = localData
                    UserBoxUtils.updateUser(user)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * 添加一条精准屏蔽规则
         * @param id 系统课程ID
         * @param day 星期 (精准屏蔽必填)
         * @param start 开始节次 (精准屏蔽必填)
         */
        fun addHiddenRule(userId: String, id: String, day: Int, start: Int): Boolean {
            return try {
                val user = UserBoxUtils.getUserById(userId) ?: return false
                var localData = user.localCurriculums
                if (localData == null) localData = JSONObject()

                if (!localData.containsKey("hidden_rules_map")) {
                    localData["hidden_rules_map"] = JSONObject()
                }
                val rulesMap = localData.getJSONObject("hidden_rules_map")

                rulesMap["$id@$day@$start"] = 1

                user.localCurriculums = localData
                UserBoxUtils.updateUser(user)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        // 获取屏蔽规则列表
        fun getHiddenRules(userId: String): Map<String, Any> {
            val user = UserBoxUtils.getUserById(userId) ?: return emptyMap()
            val localData = user.localCurriculums ?: return emptyMap()
            return localData.getJSONObject("hidden_rules_map")?.innerMap ?: emptyMap()
        }

        // --- 新增：移除屏蔽规则 (用于恢复课程) ---
        fun removeHiddenRule(userId: String, id: String, day: Int, start: Int): Boolean {
            return try {
                val user = UserBoxUtils.getUserById(userId) ?: return false
                val localData = user.localCurriculums ?: return false

                if (!localData.containsKey("hidden_rules_map")) return true

                val rulesMap = localData.getJSONObject("hidden_rules_map")
                val key = "$id@$day@$start"

                // 直接移除 Key，无需遍历
                if (rulesMap.containsKey(key)) {
                    rulesMap.remove(key)
                    user.localCurriculums = localData
                    UserBoxUtils.updateUser(user)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        // 辅助：获取所有本地课程
        fun getLocalCourses(userId: String): List<Course> {
            val list = ArrayList<Course>()
            val user = UserBoxUtils.getUserById(userId) ?: return list
            val localData = user.localCurriculums ?: return list
            val courseMap = localData.getJSONObject("local_courses") ?: return list

            for (key in courseMap.keys) {
                val obj = JSONObject.parseObject(
                    courseMap.getJSONObject(key).toJSONString(),
                    Course::class.java
                )
                if (obj != null) list.add(obj)
            }
            return list
        }
    }
}