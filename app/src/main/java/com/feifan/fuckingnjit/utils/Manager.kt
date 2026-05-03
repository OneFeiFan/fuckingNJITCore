package com.feifan.fuckingnjit.utils

import android.content.Context
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import com.feifan.fuckingnjit.utils.system.PermissionsManager

/**
 * 全局服务管理器工厂
 *
 * 提供各核心单例服务的统一访问入口，避免调用方直接依赖具体实现类。
 */
@Suppress("unused")
class Manager {
    companion object {
        /**
         * 获取权限管理器实例
         *
         * @param context 应用上下文
         * @return PermissionsManager 单例
         */
        fun getPermissionsManager(context: Context): PermissionsManager {
            return PermissionsManager.getInstance(context)
        }

        /** 获取用户管理服务实例 */
        fun getUserManager(): UserManagerImpl {
            return UserManagerImpl.getInstance()
        }

        /** 获取教务 Web 服务实例 */
        fun getWebService(): WebServiceImpl {
            return WebServiceImpl.getInstance()
        }
    }
}
