package com.feifan.fuckingnjit.service

import com.alibaba.fastjson.JSONObject


interface WebService {
    suspend fun getCurriculum() : String
    suspend fun getUserData(): JSONObject
    suspend fun getSemesterStartDate(): String
    suspend fun getEmptyClassrooms(dateRange:String,coursePeriod:String,buildingId:String): String
    suspend fun getAllSorces(): String
    suspend fun getSorcesDetail(
        classId: String,
        schoolYear: String,
        semester: String,
        courseName: String
    ): String

    suspend fun getNoticeInformation(): String
}