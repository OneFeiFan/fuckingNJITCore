package com.feifan.fuckingnjit.model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.decision.AppMode
import com.feifan.fuckingnjit.utils.security.RSAPasswordConverter
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.converter.PropertyConverter

// 用户实体
@Entity
data class User(
    @Id var uuid: Long = 0,

    @Index
    var id: String = "", // 学号，且基于学号索引
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var password: String = "",//加密过的密码
    var name: String = "",//教务系统姓名
    var yibanId: String = "", // 易班账号通常为手机号
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var yibanPassword: String = "",// 加密过的易班密码
    var storePassword: Boolean = true,//是否储存教务密码
    @Convert(converter = AppModeConverter::class, dbType = String::class)
    var currentAppMode: AppMode = AppMode.BALANCE_MODE,// app模式
    var gpa: String = "0",
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var academicProgress: JSONObject = JSONObject(),//学业进度
    @Convert(converter = JSONArrayConverter::class, dbType = String::class)
    var scores: JSONArray = JSONArray(),//全部成绩
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var curriculums: JSONObject = JSONObject(),//全部课程（for教务）
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var localCurriculums: JSONObject? = JSONObject()//全部本地课程
)

class JSONObjectConverter : PropertyConverter<JSONObject?, String?> {
    override fun convertToEntityProperty(databaseValue: String?): JSONObject? {
        if (databaseValue == null) {
            return null
        }
        return JSON.parseObject(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: JSONObject?): String? {
        if (entityProperty == null) {
            return null
        }
        return entityProperty.toJSONString()
    }
}

class JSONArrayConverter : PropertyConverter<JSONArray?, String?> {
    override fun convertToEntityProperty(databaseValue: String?): JSONArray? {
        if (databaseValue == null) {
            return null
        }
        return JSON.parseArray(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: JSONArray?): String? {
        if (entityProperty == null) {
            return null
        }
        return entityProperty.toJSONString()
    }
}

class AppModeConverter : PropertyConverter<AppMode, String> {
    override fun convertToEntityProperty(databaseValue: String?): AppMode {
        return AppMode.fromName(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: AppMode?): String {
        return entityProperty?.name ?: AppMode.BALANCE_MODE.name
    }
}