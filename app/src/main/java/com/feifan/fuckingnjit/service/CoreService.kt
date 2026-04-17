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
import com.feifan.fuckingnjit.monitor.AppUsageManager
import com.feifan.fuckingnjit.monitor.AudioMonitorManager
import com.feifan.fuckingnjit.monitor.SensorDataBufferManager
import com.feifan.fuckingnjit.monitor.SleepMotionDetector
import com.feifan.fuckingnjit.monitor.StepMonitorManager
import com.feifan.fuckingnjit.utils.TimeStrategyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CoreService : LifecycleService() {

    private val TAG = "CoreService"
    private val CHANNEL_ID = "AlwaysOnChannel"
    private val NOTIFICATION_ID = 1
    private val ACTION_HEARTBEAT = "com.feifan.fuckingnjit.ACTION_HEARTBEAT"

    // 动态退火节律参数
    private val HEARTBEAT_BASE = (2.5 * 60 * 1000).toLong()     // 2.5 分钟 (静止期高频)
    private val HEARTBEAT_EXTENDED = (5.0 * 60 * 1000).toLong() // 5.0 分钟 (活动期退火)

    private lateinit var audioManager: AudioMonitorManager
    private lateinit var timeManager: TimeStrategyManager
    private lateinit var motionDetector: SleepMotionDetector
    private lateinit var alarmManager: AlarmManager

    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastScreenState = "点亮 (ON)"
    private lateinit var screenReceiver: BroadcastReceiver
    private var lastNotifContent = ""

    override fun onCreate() {
        super.onCreate()

        timeManager = TimeStrategyManager()
        timeManager.addRange(8, 0, 22, 0)
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        audioManager = AudioMonitorManager(this); audioManager.init()
        motionDetector = SleepMotionDetector(this); motionDetector.start()
        StepMonitorManager.init(this)

        createNotificationChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FuckingNJIT:HeartbeatWakeLock")

        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_HEARTBEAT) {
            // 被精准闹钟唤醒，执行心跳管线
            lifecycleScope.launch(Dispatchers.Default) {
                executeHeartbeatPipeline()
            }
        } else if (!isRunning) {
            isRunning = true
            startForegroundServiceCompat(createNotification("监控服务启动中...", ""))
            // 首次启动，立即安排第一次心跳 (延迟5秒错开系统初始化峰值)
            scheduleNextAlarm(5000L)
        }
        return START_STICKY
    }

    /**
     * 【核心管线】定时触发的传感器融合决策
     */
    private suspend fun executeHeartbeatPipeline() {
        wakeLock?.acquire(10_000L) // 获取 WakeLock 保证 CPU 不休眠，兜底 10 秒后自动释放

        try {
            val pkgName = AppUsageManager.getForegroundPackage()
            val appName = AppUsageManager.getAppName(this, pkgName)

            // 1. 获取硬件批处理决策数据
            val isMoving = motionDetector.isUserActive()
            val motionScore = motionDetector.currentMotionScore

            var noiseDb = 0.0
            var nextAlarmInterval = HEARTBEAT_BASE

            // 2. 传感器融合决策树
            if (isMoving) {
                // 状态：活动期
                // 动作：跳过麦克风，录入安全边界值 -1.0。并拉长下一次唤醒间隔。
                noiseDb = -1.0
                nextAlarmInterval = HEARTBEAT_EXTENDED
                Log.d(TAG, "状态: 高频运动. 影子补偿: -1.0, 间隔拉长至 5 分钟.")
            } else {
                // 状态：静止期
                // 动作：抓取 250ms 快照，恢复高频监测。
                noiseDb = audioManager.captureSnapshot(250L)
                nextAlarmInterval = HEARTBEAT_BASE
                Log.d(TAG, "状态: 疑似静止. 抓取录音快照: $noiseDb dBFS.")
            }

            // 3. 后端波形兼容合成
            val mixed = if (motionScore > 0) noiseDb / motionScore else 0.0
            SensorDataBufferManager.addRecord(mixed)

            // 4. UI 状态刷新
            updateNotification(appName, noiseDb, motionScore)

            // 5. 安排下一次穿透心跳
            scheduleNextAlarm(nextAlarmInterval)

        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat Pipeline Error", e)
            scheduleNextAlarm(HEARTBEAT_BASE) // 发生异常时保底调度
        } finally {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
    }

    private fun scheduleNextAlarm(intervalMs: Long) {
        val intent = Intent(this, CoreService::class.java).apply { action = ACTION_HEARTBEAT }
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = System.currentTimeMillis() + intervalMs

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun updateNotification(
        appName: String = "",
        currentNoise: Double = 0.0,
        currentMotion: Double = 0.0
    ) {
        val noiseText = when {
            audioManager.isMicrophoneOccupied -> "⏸️ 避让"
            currentNoise == -1.0 -> "💤 免测"
            else -> "${String.format("%.1f", currentNoise)} dBFS"
        }

        val content = """
            📱 前台: ${appName.ifEmpty { "检测中..." }}
            🔊 噪音: $noiseText
            🛌 动作: ${String.format("%.1f", currentMotion)}
            🖥️ 屏幕: $lastScreenState
        """.trimIndent()

        if (content != lastNotifContent) {
            lastNotifContent = content
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification("运行中", content))
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.BigTextStyle().bigText(content).setSummaryText("监控服务")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content.replace("\n", "  "))
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 替换为你实际的图标
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startForegroundServiceCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type =
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> lastScreenState = "已锁屏 (OFF)"
                    Intent.ACTION_USER_PRESENT -> lastScreenState = "点亮 (ON)"
                }
                updateNotification()
            }
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false

        // 撤销 AlarmManager，防止服务死亡后无限诈尸拉起
        val intent = Intent(this, CoreService::class.java).apply { action = ACTION_HEARTBEAT }
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)

        if (::audioManager.isInitialized) audioManager.release()
        if (::motionDetector.isInitialized) {
            motionDetector.stop(); motionDetector.release()
        }
        StepMonitorManager.release()

        wakeLock?.let { if (it.isHeld) it.release() }

        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }

        SensorDataBufferManager.flushToDatabase()
        super.onDestroy()
    }
}