package com.feifan.fuckingnjit.model

import com.alibaba.fastjson.JSON
import com.feifan.fuckingnjit.decision.WakeUpConfiguration
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * 应用全局配置实体
 *
 * 全局单例，id 固定为 1，存储学期信息、网络认证策略、起床闹钟配置等。
 *
 * @param id 主键，全局固定为 1L
 * @param currentUserId 当前活跃的教务账号标识
 * @param semesterStartDateMs 学期开始的 Unix 时间戳（毫秒）
 * @param currentWeek 当前教学周次
 * @param wifiAuthType 校园网自动认证策略标识
 * @param smartUpdate 智能增量更新开关
 * @param wakeUpConfig 起床与闹钟相关配置
 */
@Entity
data class AppSystem(
    @Id(assignable = true)
    var id: Long = 1L,

    var currentUserId: String = "",
    var semesterStartDateMs: Long = 0L,
    var currentWeek: Int = 1,
    var wifiAuthType: String = "",
    var smartUpdate: Boolean = true,
    @Convert(converter = WakeUpConfigConverter::class, dbType = String::class)
    var wakeUpConfig: WakeUpConfiguration = WakeUpConfiguration()
)

/**
 * WakeUpConfiguration ↔ JSON 字符串 的 ObjectBox 属性转换器
 */
class WakeUpConfigConverter :
    io.objectbox.converter.PropertyConverter<WakeUpConfiguration, String> {
    override fun convertToEntityProperty(databaseValue: String?): WakeUpConfiguration {
        if (databaseValue.isNullOrEmpty()) return WakeUpConfiguration()
        return try {
            JSON.parseObject(databaseValue, WakeUpConfiguration::class.java)
                ?: WakeUpConfiguration()
        } catch (_: Exception) {
            WakeUpConfiguration()
        }
    }

    override fun convertToDatabaseValue(entityProperty: WakeUpConfiguration?): String {
        return if (entityProperty == null) "" else JSON.toJSONString(entityProperty)
    }
}