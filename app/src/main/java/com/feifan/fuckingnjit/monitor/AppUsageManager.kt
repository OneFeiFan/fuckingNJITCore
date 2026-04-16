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
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

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

        private val appCategoryMap = HashMap<String, String>()

        @Volatile
        private var isMapLoaded = false

        private val windowIdCache = LruCache<Int, String>(20)
        private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

        @Volatile
        private var lastPackageName: String = ""
        @Volatile
        private var lastSwitchTime: Long = System.currentTimeMillis()

        /**
         * 【新增】：违规应用类别名单 (在这个名单里的应用，上课玩算作摸鱼)
         */
        private val ILLEGAL_CATEGORIES = setOf("游戏", "影音娱乐", "社交通讯", "购物消费", "办公资讯")
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
                        settleLastAppDuration(packageName)
                        Log.d("AppMonitor", "当前切换至前台的应用: $currentForegroundPkg")
                    }
                }
            }
        }
    }

    /**
     * 【核心新增】：上课摸鱼结算器
     * 计算上一个应用存活了多久，如果是上课时间且玩了违规应用，则记录。
     */
    private fun settleLastAppDuration(newPackageName: String) {
        val now = System.currentTimeMillis()
        val durationMs = now - lastSwitchTime

        // 1. 如果上一个包不为空，且停留时间超过 10 秒（防止瞬间闪过的闪屏页）
        if (lastPackageName.isNotEmpty() && durationMs > 10000) {

            // 2. 查一下：刚才这段时间是在上课吗？
            if (TodayScheduleManager.isCurrentlyInClass()) {

                // 3. 查字典：上个应用是什么分类？
                val category = appCategoryMap[lastPackageName] ?: "未知"

                // 4. 判定违规并累加
                if (ILLEGAL_CATEGORIES.contains(category)) {
                    val durationMins = (durationMs / (1000 * 60)).toInt()
                    if (durationMins > 0) {
                        Log.w(TAG, "🔴 摸鱼警告！上课玩 [$lastPackageName] 达 $durationMins 分钟！")
                        saveDistractionTime(durationMins)
                    }
                }
            }
        }

        // 5. 状态机推进：无论是否违规，更新时间和包名为新应用，开启下一轮计时
        lastPackageName = newPackageName
        lastSwitchTime = now
    }

    /**
     * 【核心新增】：将摸鱼时长安全地持久化到 SharedPreferences
     * 按照每天一个 Key 来存，例如 "distraction_2026-04-05"
     */
    private fun saveDistractionTime(addedMins: Int) {
        try {
            val prefs = applicationContext.getSharedPreferences("app_usage_stats", Context.MODE_PRIVATE)
            val todayKey = "distraction_${LocalDate.now()}"

            val currentTotal = prefs.getInt(todayKey, 0)
            prefs.edit().putInt(todayKey, currentTotal + addedMins).apply()

            Log.d(TAG, "💾 今日累计摸鱼已达: ${currentTotal + addedMins} 分钟")
        } catch (e: Exception) {
            Log.e(TAG, "保存摸鱼时长失败", e)
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