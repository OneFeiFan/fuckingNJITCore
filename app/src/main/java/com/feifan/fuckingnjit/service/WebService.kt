package com.feifan.fuckingnjit.service

import android.content.Context
import com.alibaba.fastjson.JSONObject

/**
 * 教务系统 Web 服务接口
 *
 * 定义通过 WebVPN 通道访问教务系统各功能的抽象方法，
 * 实现类负责处理认证、请求构造与 HTML/JSON 解析。
 */
interface WebService {
    /**
     * 获取完整课表数据（含系统课程与本地课程的合并结果）
     */
    suspend fun getCurriculum(context: Context): JSONObject

    /** 获取当前用户的基础信息（学号、姓名等） */
    suspend fun getUserData(context: Context): JSONObject

    /** 获取学期开始日期字符串（格式 yyyy-MM-dd） */
    suspend fun getSemesterStartDate(context: Context): String

    /** 查询空教室信息 */
    suspend fun getEmptyClassrooms(
        context: Context,
        dateRange: String,
        coursePeriod: String,
        buildingId: String
    ): JSONObject

    /** 获取全部课程成绩列表 */
    suspend fun getAllSorces(context: Context, xnm: String, xqm: String): JSONObject

    /** 获取单门课程的详细成绩构成 */
    suspend fun getSorcesDetail(
        context: Context,
        classId: String,
        schoolYear: String,
        semester: String,
        courseName: String
    ): JSONObject

    /** 获取学业进度与学分完成情况 */
    suspend fun getAcademicProgress(context: Context): JSONObject
}