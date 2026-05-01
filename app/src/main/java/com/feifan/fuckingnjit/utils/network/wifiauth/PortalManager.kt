package com.feifan.fuckingnjit.utils.network.wifiauth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object PortalManager {

    /**
     * 切换拦截状态
     * @param context 上下文
     * @param enable true=开启拦截, false=恢复系统默认
     */
    fun switchStatus(context: Context, enable: Boolean) {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, PortalActivity::class.java)

        val state = if (enable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        // DONT_KILL_APP 很重要，防止切换设置时 App 闪退
        packageManager.setComponentEnabledSetting(
            componentName,
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * 检查当前是否开启
     */
    fun isEnabled(context: Context): Boolean {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, PortalActivity::class.java)
        val state = packageManager.getComponentEnabledSetting(componentName)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}