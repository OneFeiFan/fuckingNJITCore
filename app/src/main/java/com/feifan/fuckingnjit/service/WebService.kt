package com.feifan.fuckingnjit.service

import com.alibaba.fastjson.JSONObject


interface WebService {
    suspend fun getCurriculum(): JSONObject
    suspend fun getUserData(): JSONObject
    suspend fun getSemesterStartDate(): String
    suspend fun getEmptyClassrooms(
        dateRange: String,
        coursePeriod: String,
        buildingId: String
    ): String

    suspend fun getAllSorces(xnm: String, xqm: String): JSONObject
    suspend fun getSorcesDetail(
        classId: String,
        schoolYear: String,
        semester: String,
        courseName: String
    ): JSONObject

    suspend fun getNoticeInformation(): JSONObject
    suspend fun getAcademicProgress(): JSONObject
}