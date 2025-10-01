package com.feifan.fuckingnjit.database

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.utils.SecureUtil
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.converter.PropertyConverter


@Entity
data class UserData(
    @Id
    var uuid: Long = 0,
    var id: String = "",
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var password: String = "",
    var name: String = "",
    var gpa: String = "0",
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var academicProgress: JSONObject = JSONObject(),
    @Convert(converter = JSONArrayConverter::class, dbType = String::class)
    var scores: JSONArray = JSONArray(),
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var curriculums: JSONObject = JSONObject(),
    @Convert(converter = JSONObjectConverter::class, dbType = String::class)
    var localCurriculums: JSONObject? = JSONObject(),
)

class RSAPasswordConverter : PropertyConverter<String?, String?> {
    override fun convertToEntityProperty(databaseValue: String?): String? {
        if (databaseValue == null) {
            return null
        }
        return SecureUtil.rsaDecrypt(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: String?): String? {
        if (entityProperty == null) {
            return null
        }
        return SecureUtil.rsaEncrypt(entityProperty)
    }
}

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