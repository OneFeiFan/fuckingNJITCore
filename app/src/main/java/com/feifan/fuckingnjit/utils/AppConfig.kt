package com.feifan.fuckingnjit.utils

import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.utils.database.AppDataCenter

@Suppress("unused")
object AppConfig {

    // 内存级临时状态
    var inLogin = false

    fun endLogin() {
        inLogin = false
    }

    fun logout(removeCurrentUser: Boolean = true) {
        if (removeCurrentUser) {
            UserManagerImpl.getInstance().removeCurrentUser()
        }
    }

    // 设置wifi认证类型
    fun setWifiAuthType(type: String) {
        AppDataCenter.updateSystemConfig { it.wifiAuthType = type }
    }

    fun getWifiAuthType(): String {
        return AppDataCenter.getSystemConfig().wifiAuthType
    }
}