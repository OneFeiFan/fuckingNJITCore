package com.feifan.fuckingnjit.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.TypeReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

//        @Volatile
//        var lastInteractionTime: Long = 0L // 交互时间戳

//        private val launcherPackages = mutableSetOf<String>()

        // 本地包名分类数据库
        private val appCategoryMap = HashMap<String, String>()

        @Volatile
        private var isMapLoaded = false

        // 白名单：需要开启相机的分类
//        private val TARGET_CATEGORIES = setOf(
//            "影音娱乐",
//            "社交通讯",
//            "游戏",
//            "考试学习"
//        )
        private val windowIdCache = LruCache<Int, String>(20)
        private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
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

//        fun isTargetAppForCamera(context: Context, pkg: String): Boolean {
//            if (pkg.isEmpty()) return false
//            if (isSystemLauncher(context, pkg)) return false
//            return isTargetCategory(context, pkg)
//        }

        // --- 状态检测方法 (解决假死问题) ---

        /**
         * 检测无障碍服务是否“假死”
         * 返回 true 表示：系统设置里开了，但服务没跑起来（需要重启开关）
         */
        fun isServiceZombie(context: Context): Boolean {
            val isSystemSwitchOn = isAccessibilitySettingsOn(context)
            // 系统显示开了 && 内部连接标志位是 false -> 假死
            return !isSystemSwitchOn && !isServiceConnected
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

//        private fun isTargetCategory(context: Context, pkg: String): Boolean {
//            ensureMapLoaded(context)
//
//            // 1. 查表
//            val category = appCategoryMap[pkg]
//            if (category != null) {
//                return TARGET_CATEGORIES.contains(category)
//            }
//
//            // 2. 兜底策略 (针对未在数据库中的应用)
//            val lowerPkg = pkg.lowercase()
//            if (lowerPkg.contains("game") || lowerPkg.contains("video") || lowerPkg.contains("music")) {
//                return true
//            }
//
//            // 默认允许
//            return true
//        }

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
                    Log.d(
                        TAG,
                        "数据库加载完成，共 ${appCategoryMap.size} 条，耗时: ${System.currentTimeMillis() - startTime}ms"
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "加载数据库失败", e)
                    isMapLoaded = true
                }
            }
        }

//        private fun isSystemLauncher(context: Context, pkg: String): Boolean {
//            if (launcherPackages.isEmpty()) {
//                refreshLauncherPackages(context)
//            }
//            return launcherPackages.contains(pkg)
//        }
//
//        private fun refreshLauncherPackages(context: Context) {
//            try {
//                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
//                val resolveInfoList = context.packageManager.queryIntentActivities(
//                    intent,
//                    PackageManager.MATCH_DEFAULT_ONLY
//                )
//                for (info in resolveInfoList) {
//                    launcherPackages.add(info.activityInfo.packageName)
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
    }

    // --- Service 监听部分 ---

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接 (Active)")
        // 【关键】标记为已连接
        isServiceConnected = true

//        refreshLauncherPackages(this)
        // 预加载数据库
        Thread { ensureMapLoaded(this) }.start()

        try {
            val rootNode = rootInActiveWindow
            val pkg = rootNode?.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != "com.android.systemui") {
                currentForegroundPkg = pkg
                Log.d(TAG, "初始化拉取到前台包名: $pkg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取初始包名失败", e)
        }
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
        if (event != null) {
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                return
            }
            analyzeForegroundWindow()
        }
    }

//    private fun debugPrintWindowList(windowList: List<AccessibilityWindowInfo>?) {
//        if (windowList == null || windowList.isEmpty()) {
//            Log.e("AppMonitor", "🚨 警告: windowList 完全为空或为 null！请检查 XML 配置。")
//            return
//        }
//
//        Log.d("AppMonitor", "========== 开始打印所有窗口 (总数: ${windowList.size}) ==========")
//        for ((index, window) in windowList.withIndex()) {
//            // 将窗口的 int 类型的 type 转换为人类可读的字符串
//            val typeStr = when (window.type) {
//                AccessibilityWindowInfo.TYPE_APPLICATION -> "TYPE_APPLICATION (普通应用)"
//                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "TYPE_INPUT_METHOD (输入法)"
//                AccessibilityWindowInfo.TYPE_SYSTEM -> "TYPE_SYSTEM (系统窗口/状态栏/导航栏)"
//                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "TYPE_ACCESSIBILITY_OVERLAY (无障碍悬浮窗)"
//                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "TYPE_SPLIT_SCREEN_DIVIDER (分屏线)"
////                AccessibilityWindowInfo.TYPE_PICTURE_IN_PICTURE -> "TYPE_PICTURE_IN_PICTURE (画中画)"
//                else -> "未知类型 (${window.type})"
//            }
//
//            // 获取窗口在屏幕上的坐标和大小
//            val bounds = Rect()
//            window.getBoundsInScreen(bounds)
//
//            // 尝试获取包名（注意：这里仅用于调试，遍历获取 root 可能会卡顿）
//            val pkgName = try {
//                window.root?.packageName?.toString() ?: "无法获取(可能无焦点或跨域)"
//            } catch (e: Exception) {
//                "获取异常"
//            }
//
//            Log.d(
//                "AppMonitor", """
//                ▶ 窗口 [$index]:
//                  - ID: ${window.id}
//                  - 类型: $typeStr
//                  - 坐标/大小: $bounds (宽:${bounds.width()}, 高:${bounds.height()})
//                  - 状态: Focused=${window.isFocused}, Active=${window.isActive}
//                  - 包名: $pkgName
//            """.trimIndent()
//            )
//        }
//        Log.d("AppMonitor", "========== 窗口打印结束 ==========")
//    }

    private fun analyzeForegroundWindow() {
        val windowList = windows ?: return
//        debugPrintWindowList(windowList)
        // 1. 过滤掉输入法、辅助功能悬浮窗等非应用层窗口
        val effectiveWindows = windowList.filter {
            it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                    it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                    it.type != AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
        }

        if (effectiveWindows.isEmpty()) return

        var targetWindow: AccessibilityWindowInfo? = null
        var maxWindowSize = 0

        // 2. 找出最可能是前台的窗口（综合考虑焦点和面积大小）
        for (window in effectiveWindows) {
            if (window.isFocused || window.isActive) {
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val size = bounds.width() * bounds.height()

                if (size >= maxWindowSize) {
                    maxWindowSize = size
                    targetWindow = window
                }
            }
        }

        // 3. 异步获取包名，防止堵塞主线程
        targetWindow?.let { window ->
            serviceScope.launch {
                val packageName = getPackageNameSafely(window)

                if (packageName != null && packageName != currentForegroundPkg) {
                    // 过滤掉系统 UI 和桌面，避免统计干扰
                    if (packageName != "com.android.systemui") {
                        currentForegroundPkg = packageName
                        Log.d("AppMonitor", "当前切换至前台的应用: $currentForegroundPkg")
                    }
                }
            }
        }
    }

    /**
     * 安全地获取包名：先查缓存，没有再尝试获取 root 节点
     */
    private fun getPackageNameSafely(window: AccessibilityWindowInfo): String? {
        val windowId = window.id

        // 命中缓存直接返回
        windowIdCache.get(windowId)?.let { return it }

        return try {
            val rootNode = window.root
            val pkgName = rootNode?.packageName?.toString()
            if (pkgName != null) {
                windowIdCache.put(windowId, pkgName) // 存入缓存
            }
            pkgName
        } catch (e: Exception) {
            // 获取 root 节点可能会超时或抛出异常
            null
        }
    }
}