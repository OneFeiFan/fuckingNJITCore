package com.feifan.fuckingnjit.utils

import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils

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

    fun setWifiAuthTupe(type: String) {
        BaseDataBoxUtils.updateBaseData { it.wifiAuthTupe = type }
    }

    fun getWifiAuthTupe(): String {
        return BaseDataBoxUtils.getWifiAuthTupe()
    }
}