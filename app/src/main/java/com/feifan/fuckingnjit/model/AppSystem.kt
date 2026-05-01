package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * App 全局系统配置实体
 * 整个数据库中仅存在一条 ID 为 1 的记录
 */
@Entity
data class AppSystem(
    @Id(assignable = true)
    var id: Long = 1L,

    // --- 全局运行指针 ---
    var currentUserId: String = "",        // 当前活跃的教务账号 (User.id)

    // --- 教学时间轴配置 ---
    var semesterStartDateMs: Long = 0L,    // 学期开始的 Unix 时间戳 (毫秒)
    var currentWeek: Int = 1,              // 当前教学周次

    // --- 设备与系统级策略 ---
    var wifiAuthType: String = "",         // 校园网自动认证策略
    var smartUpdate: Boolean = true        // 智能增量更新开关
)