package com.feifan.fuckingnjit.model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.utils.RSAPasswordConverter
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.converter.PropertyConverter

@Entity
data class User(
    @Id var uuid: Long = 0,

    // --- 教务系统核心身份 ---
    var id: String = "", // 学号
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var password: String = "",
    var name: String = "",

    // --- 易班系统身份 (合并原 YiBan 实体) ---
    var yibanId: String = "", // 通常为手机号
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var yibanPassword: String = "",

    // --- 用户个人偏好 (从 SP 和 Base 吸收) ---
    var storePassword: Boolean = true,
    var currentAppMode: String = "BALANCE_MODE", // 取值: SCHOLAR_MODE, BALANCE_MODE, HEALTH_MODE

    // --- 教务学业数据 ---
    var gpa: String = "0",
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var academicProgress: JSONObject = JSONObject(),
    @Convert(converter = JSONArrayConverter::class, dbType = String::class)
    var scores: JSONArray = JSONArray(),
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var curriculums: JSONObject = JSONObject(),
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var localCurriculums: JSONObject? = JSONObject()
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