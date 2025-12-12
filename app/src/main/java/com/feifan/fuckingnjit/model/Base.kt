package com.feifan.fuckingnjit.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id


@Entity
data class Base(
    @Id(assignable = true)
    var uuid: Long = 0,
    var currentUserId: String = "",
    var semesterStartDate: Long = 0,  // 存储Unix时间戳
    var storePassword: Boolean = true,
    var currentWeek: Int = 1,
    var wifiAuthTupe: String? = "",
    var smartUpdate: Boolean? = true,
    var currentYiBanId: Long = 0
)