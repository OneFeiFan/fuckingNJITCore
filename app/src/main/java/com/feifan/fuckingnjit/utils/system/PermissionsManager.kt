package com.feifan.fuckingnjit.utils.system

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.feifan.fuckingnjit.monitor.AppUsageManager
import com.feifan.fuckingnjit.service.CoreService
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission


@Suppress("unused")
class PermissionsManager private constructor(private var context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PermissionsManager? = null

        fun getInstance(context: Context): PermissionsManager {
            return instance?.apply {
                this.context = context // 更新上下文对象
            } ?: synchronized(this) {
                instance?.apply {
                    this.context = context
                } ?: PermissionsManager(context).also { instance = it }
            }
        }
    }

    // 智能更新相关
    fun isSmartUpdate(): Boolean {
        return AppDataCenter.getSystemConfig().smartUpdate
    }

    fun setSmartUpdate(isSmart: Boolean) {
        AppDataCenter.updateSystemConfig { it.smartUpdate = isSmart }
    }

    // 检查和业务强相关的权限
    fun checkKeepAliveNormalPermissions(): Boolean {
        val requestList = getKeepAlivePermissionList()

        // 获取被拒绝的权限列表
        val deniedList = XXPermissions.getDeniedPermissions(context, requestList)

        // 如果被拒绝的列表为空（或者为 null），说明所有请求的权限都已经授予了
        return deniedList == null || deniedList.isEmpty()
    }

    // 申请和业务相关的基础权限
    fun requestKeepAliveNormalPermissions(callback: (Boolean, List<String>) -> Unit) {
        XXPermissions.with(context)
            .permissions(getKeepAlivePermissionList())
            .request { grantedList, deniedList ->
                val allGranted = deniedList.isEmpty()
                if (allGranted) {
                    // 如果全部权限都有了就启动监测进程（其实不应该在这里启动，但是懒得安排在其它地方了
                    val intent = Intent(context, CoreService::class.java)
                    context.startForegroundService(intent)
                }
                // 将 IPermission 转为 String 列表返回给前端，方便前端判断哪个被拒了
                val deniedStrList = deniedList.map { it.toString() }
                callback(allGranted, deniedStrList)
            }
    }

    private fun getKeepAlivePermissionList(): List<IPermission> {
        return listOf(
            PermissionLists.getRecordAudioPermission(),// 麦克风权限
            PermissionLists.getNotificationServicePermission(),//通知权限
            PermissionLists.getScheduleExactAlarmPermission(),// 精确闹钟权限
            PermissionLists.getActivityRecognitionPermission()// 安卓10之后获取运动数据权限
        )
    }

    // 麦克风权限
    fun checkRecordAudio(): Boolean =
        XXPermissions.isGrantedPermission(context, PermissionLists.getRecordAudioPermission())

    fun requestRecordAudio(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getRecordAudioPermission(), callback)

    // 安装未知应用权限
    fun checkRequestInstallPackage(): Boolean =
        XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getRequestInstallPackagesPermission()
        )

    fun requestRequestInstallPackage(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getRequestInstallPackagesPermission(), callback)

    // 通知权限
    fun checkNotification(): Boolean =
        XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getNotificationServicePermission()
        )

    fun requestNotificationServicePermission(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getNotificationServicePermission(), callback)

    // 精确闹钟
    fun checkScheduleExactAlarm(): Boolean =
        XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getScheduleExactAlarmPermission()
        )

    fun requestScheduleExactAlarm(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getScheduleExactAlarmPermission(), callback)

    // 计步器
    fun checkActivityRecognition(): Boolean {
        return XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getActivityRecognitionPermission()
        )
    }

    fun requestActivityRecognition(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getActivityRecognitionPermission(), callback)

    // 是否忽略电池优化 (后台保活关键)
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // 申请忽略电池优化
    fun requestIgnoreBatteryOptimizations() {
        try {
            // 通过一些手段直接打开请求弹窗
            // 可以绕过一点国产rom的魔改
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.fuelgauge.RequestIgnoreBatteryOptimizations"
                )
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 兜底方案，使用标准的请求
            val fallbackIntent =
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(fallbackIntent)
        }
    }

    //检查无障碍服务是否开启
    fun isAccessibilitySettingsOn(): Boolean {
        return !AppUsageManager.isServiceZombie(context)
    }

    // 跳转无障碍设置列表页
    fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun requestSinglePermission(permission: IPermission, callback: (Boolean) -> Unit) {
        XXPermissions.with(context)
            .permission(permission)
            .request { grantedList, deniedList -> // 只要没有被拒绝的，就认为是成功
                callback(deniedList.isEmpty())
            }
    }
}