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
import com.feifan.fuckingnjit.utils.database.ClassFocusRecordBoxUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

class AppUsageManager : AccessibilityService() {

    companion object {
        private const val TAG = "AppUsageManager"

        @Volatile
        private var lastInterventionTime: Long = 0L // 上次触发干预的时间戳

        @Volatile
        private var continuousViolationCount: Int = 0 // 连续违规计数器 (用于方案A衰减)
        @Volatile
        private var currentForegroundPkg: String = ""

        // 标记服务是否真正连接
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
        // 1. 检查是否在上课时间
        if (!TodayScheduleManager.isCurrentlyInClass()) return

        val category = AppCategoryRepository.getCategory(applicationContext, pkgName) ?: "未知"
        val isGroupA = CATEGORY_GROUP_A.contains(category)
        val isGroupB = CATEGORY_GROUP_B.contains(category)

        // 仅拦截 A组(娱乐) 和 B组(通信)
        if (!(isGroupA || isGroupB)) return

        // 2. 获取当前模式配置
        val prefs = this.getSharedPreferences("app_decision", MODE_PRIVATE)
        val currentModeStr = prefs.getString("current_mode", "BALANCE_MODE") ?: "BALANCE_MODE"
        val currentMode = when (currentModeStr) {
            "SCHOLAR_MODE" -> AppMode.SCHOLAR_MODE
            "HEALTH_MODE"  -> AppMode.HEALTH_MODE
            else           -> AppMode.BALANCE_MODE
        }

        val intervention = currentMode.intervention
        val baseToleranceMs = intervention.toleranceMins * 60 * 1000L
        val cooldownMs = intervention.cooldownMins * 60 * 1000L

        // 取消旧任务
        interceptJob?.cancel()

        interceptJob = serviceScope.launch {
            val now = System.currentTimeMillis()
            val timeSinceLastAction = now - lastInterventionTime

            // --- 核心机制 1：宽恕与重置 (Forgiveness) ---
            // 如果距离上次警告已经过去了超过 2 倍的冷却时间，说明学生认真听课了很久，重置容忍度
            if (lastInterventionTime > 0L && timeSinceLastAction > (cooldownMs * 2)) {
                continuousViolationCount = 0
                Log.d(TAG, "干预系统：表现良好，容忍度已重置。")
            }

            // --- 核心机制 2：容忍度梯度递减 (方案 A) ---
            // 每次违规，容忍时长变为上一次的 70%
            val decayFactor = 0.7.pow(continuousViolationCount.toDouble())
            var currentToleranceMs = (baseToleranceMs * decayFactor).toLong()

            // 设定容忍度保底底线：最少 1 分钟 (避免疯狂连弹)
            val minToleranceMs = 1 * 60 * 1000L
            if (currentToleranceMs < minToleranceMs) {
                currentToleranceMs = minToleranceMs
            }

            // --- 核心机制 3：全局冷却时间补偿 ---
            // 如果还在冷却期内，则先等待冷却期结束，再叠加本次的容忍时间
            val actualDelay = if (lastInterventionTime > 0L && timeSinceLastAction < cooldownMs) {
                (cooldownMs - timeSinceLastAction) + currentToleranceMs
            } else {
                currentToleranceMs
            }

            Log.d(TAG, "干预系统：倒计时已开启。当前连犯次数: $continuousViolationCount, 本次等待: ${actualDelay / 1000}秒")

            // 3. 开启倒计时
            delay(actualDelay)

            // 4. 超时执行动作 (Action Level 匹配)
            withContext(Dispatchers.Main) {
                when (intervention.actionLevel) {
                    1 -> {
                        // Level 1: 健康模式 (静默关怀) - 不震动，不阻断
                        Toast.makeText(applicationContext, "健康提醒：注意坐姿，让眼睛休息一下吧~", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // Level 2: 劳逸结合模式 (警告) - 震动 + 提示
                        triggerVibration()
                        Toast.makeText(applicationContext, "走神时间有点久了，快回到学习状态！", Toast.LENGTH_LONG).show()
                    }
                    3 -> {
                        // Level 3: 学霸模式 (强阻断) - 震动 + 提示 + 判断是否退回桌面
                        triggerVibration()
                        Toast.makeText(applicationContext, "学霸模式提醒：专注时间，拒绝摸鱼！", Toast.LENGTH_SHORT).show()

                        // 核心机制 4：B 组通信豁免 (仅 A 组执行 HOME 动作)
                        if (isGroupA) {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }
                }
            }

            // 5. 状态机推进：更新最后干预时间并增加“仇恨值”
            lastInterventionTime = System.currentTimeMillis()
            continuousViolationCount++

            // 6. 开启下一轮监控 (依然在前台则继续倒计时)
            handleRealTimeIntervention(pkgName)
        }
    }

    /**
     * 震动反馈
     */
    private fun triggerVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            // 定义单次节奏模式
            val singlePatternTimings = longArrayOf(0, 100, 100, 100, 100, 100, 200, 400)
            val singlePatternAmplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 200)

            // 重复次数
            val repeatCount = 3

            // 构造重复3次的总数组
            val totalTimings = mutableListOf<Long>()
            val totalAmplitudes = mutableListOf<Int>()

            for (i in 0 until repeatCount) {
                // 第一次循环需要保留开头的 0 等待，后续循环则直接衔接（不需要开头的 0 等待）
                if (i == 0) {
                    totalTimings.addAll(singlePatternTimings.toList())
                    totalAmplitudes.addAll(singlePatternAmplitudes.toList())
                } else {
                    // 跳过开头的 0 等待，直接从第一个震动指令开始衔接
                    totalTimings.addAll(singlePatternTimings.drop(1))
                    totalAmplitudes.addAll(singlePatternAmplitudes.drop(1))
                }
            }

            val effect = VibrationEffect.createWaveform(
                totalTimings.toLongArray(),
                totalAmplitudes.toIntArray(),
                -1  // -1 表示不重复，播放完整个数组即停止
            )

            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.e(TAG, "紧急震动调用失败", e)
        }
    }

    /**
     * 【事后结算流水线】：将摸鱼时长安全地持久化
     */
    /**
     * 【事后结算流水线】：将摸鱼时长安全地持久化到 ObjectBox 数据库，按单节课累加
     */
    private suspend fun settleLastAppDuration(newPackageName: String) {
        val now = System.currentTimeMillis()
        val durationMs = now - lastSwitchTime

        if (lastPackageName.isNotEmpty() && durationMs > 10000) { // 停留超过 10 秒才算数
            // 核心修改：不仅仅问是否在上课，还要拿到当前上的是哪节课
            val currentClass = TodayScheduleManager.getCurrentClassSlot()

            if (currentClass != null) {
                val category = AppCategoryRepository.getCategory(applicationContext, lastPackageName) ?: "未知"

                // A组和B组统统算作违规时长进行扣分
                if (ILLEGAL_CATEGORIES.contains(category)) {
                    val durationMins = (durationMs / (1000 * 60)).toInt()

                    // 将 LocalTime 转换为绝对的时间戳(毫秒)，供数据库使用
                    val today = LocalDate.now()
                    val startMs = today.atTime(currentClass.startTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endMs = today.atTime(currentClass.endTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    Log.w(TAG, "🔴 摸鱼警告！上课 [${currentClass.courseName}] 玩 [$lastPackageName] 达 $durationMins 分钟！")

                    // --- 核心串联：调用底层 BoxUtils 进行单节课增量写入 ---
                    ClassFocusRecordBoxUtils.addDistractionTime(
                        courseName = currentClass.courseName,
                        courseStartTime = startMs,
                        courseEndTime = endMs,
                        addedDistractionMills = durationMs // 传入精确的毫秒数
                    )
                }
            }
        }

        // 状态机推进：无论是否违规，更新时间和包名为新应用，开启下一轮计时
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