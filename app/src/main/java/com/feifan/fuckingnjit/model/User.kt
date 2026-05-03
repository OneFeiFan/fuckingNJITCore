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

/**
 * 用户实体
 *
 * 存储用户在教务系统和易班的账号凭据、学业数据以及应用运行模式偏好。
 * 密码字段均经过 RSA 加密后存储。
 */
@Entity
data class User(
    @Id var uuid: Long = 0,

    @Index
    var id: String = "",
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var password: String = "",
    var name: String = "",
    var yibanId: String = "",
    @Convert(converter = RSAPasswordConverter::class, dbType = String::class)
    var yibanPassword: String = "",
    var storePassword: Boolean = true,
    @Convert(converter = AppModeConverter::class, dbType = String::class)
    var currentAppMode: AppMode = AppMode.BALANCE_MODE,
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

/**
 * JSONObject ↔ String 的 ObjectBox 属性转换器
 */
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

/**
 * JSONArray ↔ String 的 ObjectBox 属性转换器
 */
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

/**
 * AppMode 枚举 ↔ 名称字符串 的 ObjectBox 属性转换器
 */
class AppModeConverter : PropertyConverter<AppMode, String> {
    override fun convertToEntityProperty(databaseValue: String?): AppMode {
        return AppMode.fromName(databaseValue)
    }

    override fun convertToDatabaseValue(entityProperty: AppMode?): String {
        return entityProperty?.name ?: AppMode.BALANCE_MODE.name
    }
}