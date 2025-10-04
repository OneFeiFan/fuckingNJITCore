package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission

class PermissionsManager private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PermissionsManager? = null

        fun getInstance(context: Context): PermissionsManager {
            return instance ?: synchronized(this) {
                instance ?: PermissionsManager(context).also { instance = it }
            }
        }
    }

    fun checkOverlayWindowPermission(): Boolean {
        return XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getSystemAlertWindowPermission()
        )
    }

    fun checkRequestInstallPackagePermission(): Boolean {
        return XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getRequestInstallPackagesPermission()
        )
    }

    fun requestOverlayWindowPermission(callback: (Boolean) -> Unit) {
        XXPermissions.with(context)
            .permission(PermissionLists.getSystemAlertWindowPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<IPermission>,
                    deniedList: MutableList<IPermission>
                ) {
                    val allGranted = deniedList.isEmpty()
                    if (allGranted) {
                        println("权限申请成功")
                        // 在这里处理权限请求成功的逻辑
                        callback(true)
                    } else {
                        println("权限申请被拒绝")
                        callback(false)
                    }
                }
            })
    }

    fun requestRequestInstallPackagePermission(callback: (Boolean) -> Unit) {
        XXPermissions.with(context)
            .permission(PermissionLists.getRequestInstallPackagesPermission())
            .request(object : OnPermissionCallback {
                 override fun onResult(
                     grantedList: MutableList<IPermission>,
                     deniedList: MutableList<IPermission>
                 ) {
                     val allGranted = deniedList.isEmpty()
                     if (allGranted) {
                         println("权限申请成功")
                         // 在这里处理权限请求成功的逻辑
                         callback(true)
                     } else {
                         val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(context as Activity, deniedList);
                         if(doNotAskAgain){
                             callback(true)
                         }else {
                             println("权限申请被拒绝")
                             callback(false)
                         }
                     }
                 }
             })
    }

    fun isSmartUpdate(): Boolean {
        return BaseDataBoxUtils.getSmartUpdate()
    }

    fun setSmartUpdate(isSmart: Boolean) {
        BaseDataBoxUtils.updateBaseData { it.smartUpdate = isSmart }
    }
}