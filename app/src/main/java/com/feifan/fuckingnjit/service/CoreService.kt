package com.feifan.fuckingnjit.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.monitor.AccelerometerMonitor
import com.feifan.fuckingnjit.monitor.AppUsageManager
import com.feifan.fuckingnjit.monitor.AudioMonitorManager
import com.feifan.fuckingnjit.monitor.SensorDataBufferManager
import com.feifan.fuckingnjit.monitor.StepMonitorManager
import com.feifan.fuckingnjit.utils.HeartbeatBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 核心前台服务
 *
 * 应用长期存活的守护进程，负责：
 * - 以 AlarmManager 精准闹钟驱动的心跳采样管线
 * - 加速度传感器能量采集与环境噪音检测
 * - 前台常驻通知展示实时监控状态
 * - 全局心跳广播以驱动桌面小部件刷新
 */
class CoreService : LifecycleService() {

    private val TAG = "CoreService"
    private val CHANNEL_ID = "AlwaysOnChannel"
    private val NOTIFICATION_ID = 1
    private val ACTION_TRIGGER_ENGINE = "com.feifan.fuckingnjit.ACTION_TRIGGER_ENGINE"

    private lateinit var audioManager: AudioMonitorManager
    private lateinit var motionDetector: AccelerometerMonitor
    private lateinit var alarmManager: AlarmManager

    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastScreenState = "点亮 (ON)"
    private lateinit var screenReceiver: BroadcastReceiver
    private var lastNotifContent = ""

    override fun onCreate() {
        super.onCreate()

        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        // 初始化监控模块
        audioManager = AudioMonitorManager(this)
        audioManager.init()
        motionDetector = AccelerometerMonitor(this)
        StepMonitorManager.init(this)

        createNotificationChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FuckingNJIT:HeartbeatWakeLock").apply {
                setReferenceCounted(false) // 关掉引用计数，只要调一次 release 就彻底释放
            }

        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_TRIGGER_ENGINE) {
            dispatchTick()
        } else if (!isRunning) {
            // 首次启动
            isRunning = true
            startForegroundServiceCompat(createNotification("监控服务启动中...", ""))
            // 首次启动，立即安排第一次心跳 (延迟几秒错开系统初始化峰值)
            scheduleNextAlarm(System.currentTimeMillis() + 2500L)
        }
        return START_STICKY
    }

    /**
     * 核心心跳管线：采集传感器数据并驱动全局状态更新
     *
     * 每次触发时依次执行：加速度采集 → 噪音采集 → 数据混合写入缓冲区 →
     * 更新前台通知 → 发射全局广播 → 安排下一次精准闹钟。
     */
    private fun dispatchTick() {
        lifecycleScope.launch(Dispatchers.Default) {
            // 确保采样期间 CPU 不睡死
            // 我们给 WakeLock 续期，确保它覆盖采样时间 + 后续处理时间
            wakeLock?.acquire(2000L)

            try {
                Log.i(TAG, "开始执行心跳采样管线...")

                val motionScore = motionDetector.captureEnergyScore(250L)
                val noiseDb = audioManager.captureSnapshot(250L)

                // 获取当前应用信息
                val pkgName = AppUsageManager.getForegroundPackage()
                val appName = AppUsageManager.getAppName(this@CoreService, pkgName)

                val mixed = if (motionScore > 0) noiseDb / motionScore else 0.0
                SensorDataBufferManager.addRecord(mixed)

                // 更新 UI
                updateNotification(appName, noiseDb, motionScore)

                // 发射全局广播 主要是驱动小部件
                val tickIntent = Intent(HeartbeatBus.ACTION_GLOBAL_TICK).apply {
                    setPackage(packageName)
                }
                sendBroadcast(tickIntent)

                // 安排下一次唤醒
                val nextTriggerMs = HeartbeatBus.calculateNextTickTime(System.currentTimeMillis())
                scheduleNextAlarm(nextTriggerMs)

            } catch (e: Exception) {
                Log.e(TAG, "Engine Pipeline Error", e)
                scheduleNextAlarm(System.currentTimeMillis() + HeartbeatBus.HEARTBEAT_BASE)
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            }
        }
    }

    /**
     * 安排下一次精准闹钟唤醒
     *
     * 使用 setExactAndAllowWhileIdle 实现单次精准唤醒，避免 setRepeating 的耗电问题。
     *
     * @param triggerTimeMs 目标触发时间戳（毫秒）
     */
    private fun scheduleNextAlarm(triggerTimeMs: Long) {
        val intent = Intent(this, CoreService::class.java).apply { action = ACTION_TRIGGER_ENGINE }
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 核心：使用单次精准闹钟，告别耗电的 setRepeating
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMs,
            pendingIntent
        )
    }

    /**
     * 更新前台通知内容
     *
     * 仅在内容发生实际变化时才重新提交通知，避免频繁刷新导致的闪烁。
     *
     * @param appName 当前前台应用名称
     * @param currentNoise 当前环境噪音 dBFS 值
     * @param currentMotion 当前体动能量值
     */
    private fun updateNotification(appName: String, currentNoise: Double, currentMotion: Double) {
        val noiseText = if (audioManager.isMicrophoneOccupied) "⏸️ 避让" else "${
            String.format(
                Locale.ROOT,
                "%.1f",
                currentNoise
            )
        } dBFS"

        val content = """
            📱 前台: ${appName.ifEmpty { "检测中..." }}
            🔊 噪音: $noiseText
            🛌 动作: ${String.format(Locale.ROOT, "%.1f", currentMotion)}
            🖥️ 屏幕: $lastScreenState
        """.trimIndent()

        if (content != lastNotifContent) {
            lastNotifContent = content
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification("运行中", content))
        }
    }

    /**
     * 创建前台常驻通知
     *
     * @param title 通知标题
     * @param content 通知正文内容
     * @return 构建好的 Notification 对象
     */
    private fun createNotification(title: String, content: String): Notification {
        val intent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.BigTextStyle().bigText(content).setSummaryText("监控服务")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content.replace("\n", "  "))
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * 兼容启动前台服务（处理 Android Q 的 foreground service type 要求）
     *
     * @param notification 前台通知对象
     */
    private fun startForegroundServiceCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 注册屏幕状态广播接收器
     *
     * 监听锁屏和解锁事件：锁屏时记录状态用于通知显示，
     * 解锁时立即触发一次心跳采样以保证数据及时性。
     */
    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> lastScreenState = "已锁屏 (OFF)"
                    Intent.ACTION_USER_PRESENT -> {
                        lastScreenState = "点亮 (ON)"
                        dispatchTick()
                    }
                }
            }
        }
        registerReceiver(screenReceiver, filter)
    }

    /**
     * 创建通知渠道（Android O 以上必需）
     */
    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Monitor Service", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        isRunning = false

        // 撤销 AlarmManager，防止服务死亡后无限拉起ACTION_TRIGGER_ENGINE
        val intent = Intent(this, CoreService::class.java).apply { action = ACTION_TRIGGER_ENGINE }
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)

        if (::audioManager.isInitialized) audioManager.release()
        if (::motionDetector.isInitialized) {
            motionDetector.release()
        }
        StepMonitorManager.release()

        wakeLock?.let { if (it.isHeld) it.release() }

        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        SensorDataBufferManager.flushToDatabase()
        super.onDestroy()
    }
}