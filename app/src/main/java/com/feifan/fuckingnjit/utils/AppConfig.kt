package com.feifan.fuckingnjit.utils

import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.utils.database.AppDataCenter

/**
 * 应用全局配置与运行状态管理
 *
 * 管理登录状态标记、登出流程以及校园网认证策略等内存级临时状态。
 */
@Suppress("unused")
object AppConfig {

    /** 标记当前是否处于登录流程中 */
    var inLogin = false

    /** 结束登录状态 */
    fun endLogin() {
        inLogin = false
    }

    /**
     * 执行登出操作
     *
     * @param removeCurrentUser 是否同时清除当前用户标记
     */
    fun logout(removeCurrentUser: Boolean = true) {
        if (removeCurrentUser) {
            UserManagerImpl.getInstance().removeCurrentUser()
        }
    }

    /**
     * 设置校园网自动认证策略
     *
     * @param type 认证类型标识
     */
    fun setWifiAuthType(type: String) {
        AppDataCenter.updateSystemConfig { it.wifiAuthType = type }
    }

    /**
     * 获取当前设置的校园网认证类型
     *
     * @return 认证类型标识字符串
     */
    fun getWifiAuthType(): String {
        return AppDataCenter.getSystemConfig().wifiAuthType
    }
}