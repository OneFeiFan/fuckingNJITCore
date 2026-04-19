package com.feifan.fuckingnjit.monitor

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.feifan.fuckingnjit.model.AppMode
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import com.feifan.fuckingnjit.utils.database.AppCategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class AppUsageManager : AccessibilityService() {

    companion object {
        private const val TAG = "AppUsageManager"

        @Volatile
        private var currentForegroundPkg: String = ""

        // 【新增】标记服务是否真正连接
        @Volatile
        private var isServiceConnected = false

        private val windowIdCache = LruCache<Int, String>(20)
        private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        @Volatile
        private var lastPackageName: String = ""

        @Volatile
        private var lastSwitchTime: Long = System.currentTimeMillis()

        // A组：强制阻断名单（触发返回桌面）
        private val CATEGORY_GROUP_A = setOf("游戏", "影音娱乐", "购物消费")

        // B组：通信豁免名单（不强制退出，但记录扣分）
        private val CATEGORY_GROUP_B = setOf("社交通讯", "办公资讯")

        // 违规总名单（用于课后计算总分）
        private val ILLEGAL_CATEGORIES = CATEGORY_GROUP_A + CATEGORY_GROUP_B

        private val appLabelCache = ConcurrentHashMap<String, String>()

        fun getForegroundPackage(): String = currentForegroundPkg

        suspend fun getAppName(context: Context, pkg: String): String {
            if (pkg.isEmpty()) return "等待检测..."
            val label = appLabelCache.getOrPut(pkg) {
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    appInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (pkg.contains(".")) pkg.substringAfterLast(".") else pkg
                }
            }
            val category = AppCategoryRepository.getCategory(context, pkg) ?: "未知"
            return "$label [$category]"
        }

        /**
         * 检测无障碍服务是否“假死”
         * 返回 true 表示：系统设置里开了，但服务没跑起来（需要重启开关）
         */
        fun isServiceZombie(context: Context): Boolean {
            return !isAccessibilitySettingsOn(context) && !isServiceConnected // 系统显示开了 && 内部连接标志位是 false -> 假死
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
    }

    private var interceptJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    // --- Service 监听部分 ---

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接 (Active)")
        isServiceConnected = true

        // 1. 注册屏幕状态监听（处理锁屏边缘场景）
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    Log.d(TAG, "屏幕已锁定，触发结算并拆除定时炸弹")
                    // 动作 A：立刻拆除阻断炸弹，防止息屏状态下被踢回桌面
                    interceptJob?.cancel()
                    // 动作 B：强制结算当前存活的应用时长
                    serviceScope.launch {
                        settleLastAppDuration("LOCK_SCREEN")
                    }
                }
            }
        }
        registerReceiver(screenReceiver, filter)

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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        screenReceiver?.let { unregisterReceiver(it) }
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

                if (packageName != null && packageName != currentForegroundPkg && packageName != "com.android.systemui") {
                    currentForegroundPkg = packageName

                    // 【核心控制流转】：当应用切换时，先结算上一个应用的账，再为新应用挂载炸弹
                    handleAppSwitch(packageName)
                }
            }
        }
    }

    /**
     * 统一处理应用切换逻辑（解耦统计与阻断）
     */
    private suspend fun handleAppSwitch(newPackageName: String) {
        // 1. 立刻拆除上一个应用的定时炸弹
        interceptJob?.cancel()

        // 2. 结算上一个应用的时长账单
        settleLastAppDuration(newPackageName)

        // 3. 为新切入的应用进行实时拦截判定
        handleRealTimeIntervention(newPackageName)
    }

    /**
     * 【实时阻断流水线】
     */
    private suspend fun handleRealTimeIntervention(pkgName: String) {
        // 如果当前不在上课时间，直接放行
        if (!TodayScheduleManager.isCurrentlyInClass()) return

        val category = AppCategoryRepository.getCategory(applicationContext, pkgName) ?: "未知"

        // 仅当应用属于 A 组强制阻断名单时，才考虑发射回桌面
        if (CATEGORY_GROUP_A.contains(category)) {

            val prefs = this.getSharedPreferences("app_decision", Context.MODE_PRIVATE)
            val currentModeStr =
                prefs.getString("current_mode", "BALANCE_MODE") ?: "BALANCE_MODE"

            println("策略模式:$currentModeStr")

            val currentMode = when (currentModeStr) {
                "SCHOLAR_MODE" -> AppMode.SCHOLAR_MODE
                "HEALTH_MODE" -> AppMode.HEALTH_MODE
                else -> AppMode.BALANCE_MODE
            }

            // 只有学霸模式才开启强制阻断
            if (currentMode == AppMode.SCHOLAR_MODE) {
                // 读取该模式的专属容忍阈值（这里假设我们已经按照讨论重构了 AppMode）
                val toleranceMins = currentMode.intervention.toleranceMins
                val toleranceMs = toleranceMins * 60 * 1000L

                interceptJob = serviceScope.launch {
                    delay(toleranceMs)

                    // 炸弹引爆！踢回桌面
                    performGlobalAction(GLOBAL_ACTION_HOME)

                    // 警告提示与震动反馈 (切到主线程展示 Toast)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "学霸模式已强制中断娱乐应用！",
                            Toast.LENGTH_LONG
                        ).show()
                        triggerVibration()
                    }
                }
            }
        }
    }

    /**
     * 震动反馈
     */
    private fun triggerVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "震动调用失败", e)
        }
    }

    /**
     * 【事后结算流水线】：将摸鱼时长安全地持久化
     */
    private suspend fun settleLastAppDuration(newPackageName: String) {
        val now = System.currentTimeMillis()
        val durationMs = now - lastSwitchTime

        if (lastPackageName.isNotEmpty() && durationMs > 10000) { // 停留超过 10 秒才算数
            if (TodayScheduleManager.isCurrentlyInClass()) {
                val category =
                    AppCategoryRepository.getCategory(applicationContext, lastPackageName) ?: "未知"

                // A组和B组统统算作违规时长进行扣分
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
            val prefs =
                applicationContext.getSharedPreferences("app_usage_stats", Context.MODE_PRIVATE)
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
                windowIdCache.put(windowId, pkgName)
            }
            pkgName
        } catch (e: Exception) {
            // 获取 root 节点可能会超时或抛出异常
            null
        }
    }
}