package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.feifan.fuckingnjit.monitor.AppUsageManager
import com.feifan.fuckingnjit.service.CoreService
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission


/**
 * 权限与业务状态管理器 (专为 uni-app 桥接设计)
 * 采用 object 声明，彻底杜绝 Context 内存泄漏
 */
class PermissionsManager private constructor(private var context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PermissionsManager? = null

        fun getInstance(context: Context): PermissionsManager {
            return instance?.apply {
                this.context = context
            } ?: synchronized(this) {
                instance?.apply {
                    this.context = context
                } ?: PermissionsManager(context).also { instance = it }
            }
        }
    }
    // ==========================================
    // 1. 业务级配置 (同步读取/修改)
    // ==========================================

    fun isSmartUpdate(): Boolean {
        return BaseDataBoxUtils.getSmartUpdate()
    }

    fun setSmartUpdate(isSmart: Boolean) {
        BaseDataBoxUtils.updateBaseData { it.smartUpdate = isSmart }
    }


    // ==========================================
    // 2. 首次启动：批量权限控制 (KeepAlive 核心权限群)
    // ==========================================

    /**
     * 检查：是否已全部授予保活基础权限
     */
    fun checkKeepAliveNormalPermissions(): Boolean {
        val requestList = getKeepAlivePermissionList()

        // 获取被拒绝的权限列表
        val deniedList = XXPermissions.getDeniedPermissions(context, requestList)

        // 如果被拒绝的列表为空（或者为 null），说明所有请求的权限都已经授予了
        return deniedList == null || deniedList.isEmpty()
    }

    /**
     * 申请：一键批量申请保活基础权限
     * callback 回调参数: (是否全部通过, 被拒绝的权限列表集合)
     */
    fun requestKeepAliveNormalPermissions(callback: (Boolean, List<String>) -> Unit) {
        XXPermissions.with(context)
            .permissions(getKeepAlivePermissionList())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<IPermission>,
                    deniedList: MutableList<IPermission>
                ) {
                    val allGranted = deniedList.isEmpty()
                    if (allGranted) {
                        val intent = Intent(context, CoreService::class.java)
                        context.startForegroundService(intent)
                    }
                    // 将 IPermission 转为 String 列表返回给前端，方便前端判断哪个被拒了
                    val deniedStrList = deniedList.map { it.toString() }
                    callback(allGranted, deniedStrList)
                }
            })
    }

    private fun getKeepAlivePermissionList(): List<IPermission> {
        return listOf(
            PermissionLists.getRecordAudioPermission(),
            PermissionLists.getNotificationServicePermission(),
//            PermissionLists.getWriteExternalStoragePermission(),
//            PermissionLists.getPackageUsageStatsPermission(),
            PermissionLists.getScheduleExactAlarmPermission(),
            PermissionLists.getActivityRecognitionPermission()
        )
    }


    // ==========================================
    // 3. 设置页面：单个权限精细化控制 (原子操作)
    // ==========================================
    // --- 麦克风/录音权限 ---
    fun checkRecordAudio(): Boolean =
        XXPermissions.isGrantedPermission(context, PermissionLists.getRecordAudioPermission())

    fun requestRecordAudio(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getRecordAudioPermission(), callback)

    // --- 安装未知应用权限 ---
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

//    // 应用使用状态
//    fun checkPackageUsageStats(): Boolean =
//        XXPermissions.isGrantedPermission(context, PermissionLists.getPackageUsageStatsPermission())
//
//    fun requesPackageUsageStats(callback: (Boolean) -> Unit) =
//        requestSinglePermission( PermissionLists.getPackageUsageStatsPermission(), callback)

    // 精确闹钟
    fun checkScheduleExactAlarm(): Boolean =
        XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getScheduleExactAlarmPermission()
        )

    // --- 运动与健身权限 (计步器) ---
    fun checkActivityRecognition(): Boolean {
        // XXPermissions 原生常量为 Permission.ACTIVITY_RECOGNITION
        return XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getActivityRecognitionPermission()
        )
    }

    fun requestActivityRecognition(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getActivityRecognitionPermission(), callback)

    fun requestScheduleExactAlarm(callback: (Boolean) -> Unit) =
        requestSinglePermission(PermissionLists.getScheduleExactAlarmPermission(), callback)


    // ==========================================
    // 4. 特殊系统级权限 (无障碍 & 电池优化)
    // 注意：这类权限无法直接回调结果，只能跳系统设置
    // ==========================================

    /**
     * 检查：是否忽略电池优化 (后台保活关键)
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 申请：跳转到忽略电池优化设置页
     */
    fun requestIgnoreBatteryOptimizations() {
        try {
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
            val fallbackIntent =
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(fallbackIntent)
        }
    }

    /**
     * 检查：无障碍服务是否开启
     * @param serviceClassName 你的无障碍服务完整类名，例如 "com.feifan.keepalive.AppUsageManager"
     */
    fun isAccessibilitySettingsOn(): Boolean {
        return !AppUsageManager.isServiceZombie(context)
//        var accessibilityEnabled = 0
//        val service = "${context.packageName}/${AppUsageManager::class.java.name}"
//        println("无障碍名称：$service")
//        try {
//            accessibilityEnabled = Settings.Secure.getInt(
//                context.contentResolver,
//                Settings.Secure.ACCESSIBILITY_ENABLED
//            )
//        } catch (e: Settings.SettingNotFoundException) {
//            return false
//        }
//
//        if (accessibilityEnabled == 1) {
//            val settingValue = Settings.Secure.getString(
//                context.contentResolver,
//                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
//            )
//            if (settingValue != null) {
//                val splitter = TextUtils.SimpleStringSplitter(':')
//                splitter.setString(settingValue)
//                while (splitter.hasNext()) {
//                    val accessibilityService = splitter.next()
//                    if (accessibilityService.equals(service, ignoreCase = true)) {
//                        return true
//                    }
//                }
//            }
//        }
//        return false
    }

    /**
     * 申请：跳转到无障碍设置列表页
     */
    fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ==========================================
    // 私有辅助方法
    // ==========================================

    private fun requestSinglePermission(permission: IPermission, callback: (Boolean) -> Unit) {
        XXPermissions.with(context)
            .permission(permission)
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: MutableList<IPermission>,
                    deniedList: MutableList<IPermission>
                ) {
                    // 只要没有被拒绝的，就认为是成功
                    callback(deniedList.isEmpty())
                }
            })
    }
}