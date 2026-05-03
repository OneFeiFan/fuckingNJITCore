package com.feifan.fuckingnjit.utils.academic

import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.Course
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import java.util.UUID

/**
 * 本地课表管理器，负责本地课程的新增、修改、删除及屏蔽规则管理。
 *
 * 所有操作基于当前用户的本地课程 JSON 树，数据最终通过 ObjectBox 持久化。
 */
class CourseManager {

    companion object {
        /**
         * 新增或更新一条本地课程。
         *
         * 如果传入的 [Course.id] 为空，会自动生成 `local_` 前缀的 UUID 作为主键，
         * 并强制将 [Course.source] 标记为本地来源（1）。
         *
         * @param course 待保存的课程实体
         * @return 是否保存成功，无登录用户时返回 false
         */
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

        /**
         * 根据课程 ID 从本地课表中删除指定课程。
         *
         * @param courseId 待删除的课程主键
         * @return 是否删除成功，课程不存在时也返回 false
         */
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

        /**
         * 获取当前用户的所有本地课程列表。
         *
         * @return 本地课程集合，无数据时返回空列表
         */
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

        /**
         * 为指定课程添加屏蔽规则，使其在课表界面中隐藏。
         *
         * 内部使用 `课程ID@星期@节次` 格式作为组合键存储。
         *
         * @param id    课程 ID
         * @param day   星期几（1-7）
         * @param start 起始节次
         * @return 是否添加成功
         */
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

        /**
         * 获取当前用户已设置的所有课程屏蔽规则。
         *
         * @return 键为 `课程ID@星期@节次` 格式的规则映射，无规则时返回空 map
         */
        fun getHiddenRules(): Map<String, Any> {
            val user = AppDataCenter.getCurrentUser() ?: return emptyMap()
            val localData = user.localCurriculums ?: return emptyMap()
            return localData.getJSONObject("hidden_rules_map")?.innerMap ?: emptyMap()
        }

        /**
         * 移除指定课程的屏蔽规则，使其重新在课表界面中显示。
         *
         * @param id    课程 ID
         * @param day   星期几（1-7）
         * @param start 起始节次
         * @return 是否移除成功，规则本身不存在时也返回 true
         */
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