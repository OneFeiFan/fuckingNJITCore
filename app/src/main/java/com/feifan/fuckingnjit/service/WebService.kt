package com.feifan.fuckingnjit.service

import android.content.Context
import com.alibaba.fastjson.JSONObject


interface WebService {
    suspend fun getCurriculum(context: Context): JSONObject
    suspend fun getUserData(context: Context): JSONObject
    suspend fun getSemesterStartDate(context: Context): String
    suspend fun getEmptyClassrooms(
        context: Context,
        dateRange: String,
        coursePeriod: String,
        buildingId: String
    ): JSONObject

    suspend fun getAllSorces(context: Context, xnm: String, xqm: String): JSONObject
    suspend fun getSorcesDetail(
        context: Context,
        classId: String,
        schoolYear: String,
        semester: String,
        courseName: String
    ): JSONObject

    //    suspend fun getNoticeInformation(): JSONObject
    suspend fun getAcademicProgress(context: Context): JSONObject
}