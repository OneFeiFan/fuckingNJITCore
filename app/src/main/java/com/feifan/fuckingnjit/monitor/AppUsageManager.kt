package com.feifan.fuckingnjit.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.TypeReference

/**
 * 二合一组件：无障碍服务 + 应用策略工具
 * 功能：
 * 1. 监听前台应用（防 SystemUI 遮挡）
 * 2. 使用 Fastjson 高速加载本地分类数据库
 * 3. 提供服务状态检测（防假死）
 */
class AppUsageManager : AccessibilityService() {

    companion object {
        private const val TAG = "AppUsageManager"

        @Volatile
        private var currentForegroundPkg: String = ""

        // 【新增】标记服务是否真正连接
        @Volatile
        private var isServiceConnected = false

        @Volatile
        var lastInteractionTime: Long = 0L // 交互时间戳

        private val launcherPackages = mutableSetOf<String>()

        // 本地包名分类数据库
        private val appCategoryMap = HashMap<String, String>()

        @Volatile
        private var isMapLoaded = false

        // 白名单：需要开启相机的分类
        private val TARGET_CATEGORIES = setOf(
            "影音娱乐",
            "社交通讯",
            "游戏",
            "考试学习"
        )

        // --- 静态对外接口 ---

        fun getForegroundPackage(): String {
            return currentForegroundPkg
        }

        fun getAppName(context: Context, pkg: String): String {
            if (pkg.isEmpty()) return "等待检测..."

            ensureMapLoaded(context)
            val label = try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                appInfo.loadLabel(pm).toString()
            } catch (e: Exception) {
                e.printStackTrace()
                if (pkg.contains(".")) pkg.substringAfterLast(".") else pkg
            }

            val category = appCategoryMap[pkg]
            return if (category != null) "$label [$category]" else label
        }

        fun isTargetAppForCamera(context: Context, pkg: String): Boolean {
            if (pkg.isEmpty()) return false
            if (isSystemLauncher(context, pkg)) return false
            return isTargetCategory(context, pkg)
        }

        // --- 状态检测方法 (解决假死问题) ---

        /**
         * 检测无障碍服务是否“假死”
         * 返回 true 表示：系统设置里开了，但服务没跑起来（需要重启开关）
         */
        fun isServiceZombie(context: Context): Boolean {
            val isSystemSwitchOn = isAccessibilitySettingsOn(context)
            // 系统显示开了 && 内部连接标志位是 false -> 假死
            return isSystemSwitchOn && !isServiceConnected
        }

        /**
         * 检查服务是否正常运行
         */
        fun isServiceRunning(): Boolean {
            return isServiceConnected
        }

        /**
         * 检查系统设置里的开关是否开启
         */
        fun isAccessibilitySettingsOn(context: Context): Boolean {
            var accessibilityEnabled = 0
            val service = "${context.packageName}/${AppUsageManager::class.java.name}"

            try {
                accessibilityEnabled = Settings.Secure.getInt(
                    context.applicationContext.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (e: Settings.SettingNotFoundException) {
                return false
            }

            if (accessibilityEnabled == 1) {
                val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
                val settingValue = Settings.Secure.getString(
                    context.applicationContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (settingValue != null) {
                    mStringColonSplitter.setString(settingValue)
                    while (mStringColonSplitter.hasNext()) {
                        val accessibilityService = mStringColonSplitter.next()
                        if (accessibilityService.equals(service, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        // --- 内部辅助方法 ---

        private fun isTargetCategory(context: Context, pkg: String): Boolean {
            ensureMapLoaded(context)

            // 1. 查表
            val category = appCategoryMap[pkg]
            if (category != null) {
                return TARGET_CATEGORIES.contains(category)
            }

            // 2. 兜底策略 (针对未在数据库中的应用)
            val lowerPkg = pkg.lowercase()
            if (lowerPkg.contains("game") || lowerPkg.contains("video") || lowerPkg.contains("music")) {
                return true
            }

            // 默认允许
            return true
        }

        /**
         * 使用 Fastjson 加载数据
         */
        private fun ensureMapLoaded(context: Context) {
            if (isMapLoaded) return

            synchronized(this) {
                if (isMapLoaded) return
                try {
                    Log.d(TAG, "正在加载应用分类数据库 (Fastjson)...")
                    val startTime = System.currentTimeMillis()

                    val jsonString = context.assets.open("app_mapping.json").bufferedReader().use {
                        it.readText()
                    }

                    val map = JSON.parseObject(
                        jsonString,
                        object : TypeReference<Map<String, String>>() {}
                    )

                    if (map != null) {
                        appCategoryMap.putAll(map)
                    }

                    isMapLoaded = true
                    Log.d(TAG, "数据库加载完成，共 ${appCategoryMap.size} 条，耗时: ${System.currentTimeMillis() - startTime}ms")

                } catch (e: Exception) {
                    Log.e(TAG, "加载数据库失败", e)
                    isMapLoaded = true
                }
            }
        }

        private fun isSystemLauncher(context: Context, pkg: String): Boolean {
            if (launcherPackages.isEmpty()) {
                refreshLauncherPackages(context)
            }
            return launcherPackages.contains(pkg)
        }

        private fun refreshLauncherPackages(context: Context) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolveInfoList = context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                for (info in resolveInfoList) {
                    launcherPackages.add(info.activityInfo.packageName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Service 监听部分 ---

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接 (Active)")
        // 【关键】标记为已连接
        isServiceConnected = true

        refreshLauncherPackages(this)
        // 预加载数据库
        Thread { ensureMapLoaded(this) }.start()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "无障碍服务断开连接 (Unbind)")
        // 【关键】标记为断开
        isServiceConnected = false
        currentForegroundPkg = ""
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断 (Interrupt)")
        isServiceConnected = false
        currentForegroundPkg = ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isServiceConnected) isServiceConnected = true

        // 1. 交互监听 (核心：推断人在线)
        if (event != null) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
        }

        // 2. 窗口检测
        val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
        if (rootNode != null) {
            val pkg = rootNode.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != "com.android.systemui") {
                currentForegroundPkg = pkg
            }
        } else if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != "com.android.systemui") {
                currentForegroundPkg = pkg
            }
        }
    }
}