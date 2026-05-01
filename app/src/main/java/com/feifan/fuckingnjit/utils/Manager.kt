package com.feifan.fuckingnjit.utils

import android.content.Context
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import com.feifan.fuckingnjit.utils.system.PermissionsManager


class Manager {
    companion object {

        fun getPermissionsManager(context: Context): PermissionsManager {
            return PermissionsManager.getInstance(context)
        }

        fun getUserManager(): UserManagerImpl {
            return UserManagerImpl.getInstance()
        }

        fun getWebService(): WebServiceImpl {
            return WebServiceImpl.getInstance()
        }

    }
}
