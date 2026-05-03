package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

// APP全局配置实体
@Entity
data class AppSystem(
    @Id(assignable = true)
    var id: Long = 1L, //全局只需一个实例，id固定为1

    var currentUserId: String = "",        // 当前活跃的教务账号
    var semesterStartDateMs: Long = 0L,    // 学期开始的 Unix 时间戳 (毫秒)
    var currentWeek: Int = 1,              // 当前教学周次
    var wifiAuthType: String = "",         // 校园网自动认证策略
    var smartUpdate: Boolean = true        // 智能增量更新开关
)