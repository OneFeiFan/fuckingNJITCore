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
import com.feifan.fuckingnjit.utils.HeartbeatBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CoreService : LifecycleService() {

    private val TAG = "CoreService"
    private val CHANNEL_ID = "AlwaysOnChannel"
    private val NOTIFICATION_ID = 1
    private val ACTION_TRIGGER_ENGINE = "com.feifan.fuckingnjit.ACTION_TRIGGER_ENGINE"

    private lateinit var audioManager: AudioMonitorManager
    private lateinit var motionDetector: SleepMotionDetector
    private lateinit var alarmManager: AlarmManager

    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastScreenState = "点亮 (ON)"
    private lateinit var screenReceiver: BroadcastReceiver
    private var lastNotifContent = ""

    override fun onCreate() {
        super.onCreate()

        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 初始化监控模块
        audioManager = AudioMonitorManager(this)
        audioManager.init()
        motionDetector = SleepMotionDetector(this)
        StepMonitorManager.init(this)

        createNotificationChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FuckingNJIT:HeartbeatWakeLock").apply {
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
     * 【核心管线】分发心跳，驱动全 App
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

    private fun updateNotification(appName: String, currentNoise: Double, currentMotion: Double) {
        val noiseText = if (audioManager.isMicrophoneOccupied) "⏸️ 避让" else "${
            String.format(
                "%.1f",
                currentNoise
            )
        } dBFS"

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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
                    Intent.ACTION_USER_PRESENT -> {
                        lastScreenState = "点亮 (ON)"
                        dispatchTick()
                    }
                }
            }
        }
        registerReceiver(screenReceiver, filter)
    }

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
        }

        SensorDataBufferManager.flushToDatabase()
        super.onDestroy()
    }
}