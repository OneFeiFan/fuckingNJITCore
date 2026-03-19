package com.feifan.fuckingnjit.service

//noinspection SuspiciousImport
import android.R
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
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.feifan.fuckingnjit.monitor.AppUsageManager
import com.feifan.fuckingnjit.monitor.AudioMonitorManager
import com.feifan.fuckingnjit.monitor.SleepMotionDetector
import com.feifan.fuckingnjit.utils.TimeStrategyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale

class CoreService : LifecycleService() {

    private val TAG = "CoreService"
    private val CHANNEL_ID = "AlwaysOnChannel"
    private val NOTIFICATION_ID = 1

    // 统一心跳间隔: 5秒
    private val HEARTBEAT_INTERVAL = 5000L
//    private var tickCount = 0L

    // --- 管理器群 ---
//    private lateinit var cameraManager: CameraPulseManager
//    private lateinit var appManager: AppUsageManager
    private lateinit var audioManager: AudioMonitorManager
    private lateinit var timeManager: TimeStrategyManager // 【新增】
    private lateinit var motionDetector: SleepMotionDetector

    //    private lateinit var proximityManager: ProximityManager
    // 基础状态
    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null

    //    private var audioTrack: AudioTrack? = null
    private var lastScreenState = "点亮 (ON)"

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // 屏幕广播
    private lateinit var screenReceiver: BroadcastReceiver

    private lateinit var appName: String

    private var lastNotifContent = ""

    override fun onCreate() {
        super.onCreate()

        // 初始化
        timeManager = TimeStrategyManager()
        timeManager.addRange(8, 0, 22, 0) // 默认全天

        audioManager = AudioMonitorManager(this); audioManager.init()
        motionDetector = SleepMotionDetector(this)
//        proximityManager = ProximityManager(this); proximityManager.start()
//        cameraManager = CameraPulseManager(this, this); cameraManager.init()
//
//        cameraManager.onStateChanged = { updateNotification() }

        createNotificationChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlwaysOn:WakeLock")
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundServiceCompat(createNotification("监控服务启动中...", ""))
        if (!isRunning) {
            isRunning = true
//            tickCount = 0
            lifecycleScope.launch(Dispatchers.Default) {
                while (isActive && isRunning) {
                    onHeartbeat()
                    delay(HEARTBEAT_INTERVAL) // 挂起 5 秒，不占用任何 CPU 资源
                }
            }
        }
        return START_STICKY
    }

    /**
     * 【核心】统一心跳回调 (每 5秒 一次)
     */
    private suspend fun onHeartbeat() {
        try {
            // 1. 获取上下文
            val pkgName = AppUsageManager.getForegroundPackage()
            val appName = AppUsageManager.getAppName(this, pkgName)
            var motionScore = 0.0
            var noiseDb = -1.0
            coroutineScope {
// 任务 A：音频检测并发 (跑在 IO 线程，专门应对底层硬件阻塞)
                val audioDeferred = async(Dispatchers.IO) {
                    // 【极度健壮的保护】：最多只给麦克风 500 毫秒的时间
                    withTimeoutOrNull(500) {
                        audioManager.detectNoise()
                        // 录音结束后，直接返回内部的变量，解决线程可见性问题
                        audioManager.lastNoiseDb
                    }
                }
// 任务 B：运动检测并发 (跑在 Default 线程计算)
                val motionDeferred = async(Dispatchers.Default) {
                    // 此处调用你之前改成同步阻塞的触发方法
                    motionDetector.triggerSampleSync()
                }

                // 终点汇合：等待双方结果
                motionScore = motionDeferred.await()

                // 如果音频任务超过 500ms 卡死，withTimeoutOrNull 会安全掐断并返回 null
                noiseDb = audioDeferred.await() ?: run {
                    Log.e(TAG, "⚠️ 麦克风底层死锁或超时，主动丢弃本次音频数据！")
                    -1.0
                }
            }
            // 2. 环境准入 (Time & Screen & App)
//            val isEnvOk = timeManager.isCurrentTimeAllowed() &&
//                    !lastScreenState.contains("OFF") &&
//                    AppUsageManager.isTargetAppForCamera(this, pkgName)

            // 3. 物理/行为阻断 (推断)

            // A. 动作推断
//            val motionScore = motionDetector.triggerSampleSync() // 触发 300ms 采样
//            val isMoving = motionScore > 1.5

            // B. 交互推断 (10秒内有操作)
//            val isInteracting = (System.currentTimeMillis() - AppUsageManager.lastInteractionTime) < 10000

            // C. 遮挡推断
//            val isCovered = proximityManager.isCovered

            // --- 决策 ---

//            if (isMoving || isInteracting) {
            // 有人活动 -> 充值信任 -> 跳过相机
//                cameraManager.refreshTrust()
//                cameraManager.setExternalAllow(false) // 暂时不需要相机
//            } else {
            // 静止状态 -> 检查是否允许相机
            // 必须环境合适 + 没遮挡
//                val allowCamera = isEnvOk && !isCovered
//                cameraManager.setExternalAllow(allowCamera)

            // 触发检测 (根据 Trust 状态频率)
//                val requiredInterval = cameraManager.getRequiredInterval()
//                val currentSec = tick * (HEARTBEAT_INTERVAL / 1000)

//                if (allowCamera && (currentSec % requiredInterval == 0L)) {
//                    cameraManager.triggerCheck()
//                }
//            }

            // 4. 音频检测
//            audioManager.detectNoise()

            // 5. 记录数据
//            val noiseDb = audioManager.lastNoiseDb
//            MotionLogger.saveDecibelRecord(this, motionScore)
//            NoiseLogger.saveDecibelRecord(this, noiseDb)
            if (motionScore > 0) noiseDb / motionScore else 0.0
//            mix.saveDecibelRecord(this, mixed)

            updateNotification(appName)
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat error", e)
        }
    }

    private fun updateNotification(appName: String = "") {
        val noiseText = if (audioManager.isMicrophoneOccupied) "⏸️ 避让" else "${
            String.format("%.1f", audioManager.lastNoiseDb)
        } dB"
        val content = """
            📱 前台: ${appName.ifEmpty { "检测中..." }}
            🔊 噪音: $noiseText
            🛌 动作: ${String.format("%.1f", motionDetector.currentMotionScore)}
            🖥️ 屏幕: $lastScreenState
        """.trimIndent()

        if (content != lastNotifContent) {
            lastNotifContent = content
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification("运行中", content))
        }
    }

    // --- 辅助方法 ---

    private fun createNotification(title: String, content: String): Notification {
        val packageName = this.packageName
        val intent: Intent? = this.packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.BigTextStyle().bigText(content).setSummaryText("监控服务")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content.replace("\n", "  "))
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startForegroundServiceCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 删除了 Android 14 单独加 CAMERA 的逻辑，保持和 Manifest 一致
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

//    private fun startKeepAliveAudio() {
//        Thread {
//            try {
//                val bufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
//                audioTrack = AudioTrack.Builder()
//                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
//                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
//                    .setBufferSizeInBytes(bufferSize)
//                    .setTransferMode(AudioTrack.MODE_STREAM)
//                    .build()
//                audioTrack?.play()
//                val silence = ByteArray(bufferSize)
//                while (isRunning) {
//                    audioTrack?.write(silence, 0, silence.size)
//                    Thread.sleep(1000)
//                }
//            } catch (e: Exception) { Log.e(TAG, "Audio Error", e) }
//        }.start()
//    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        wakeLock?.acquire()
                        lastScreenState = "已锁屏 (OFF)"
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        wakeLock?.release()
                        lastScreenState = "点亮 (ON)"
                    }
                }
                updateNotification()
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

//        if (::cameraManager.isInitialized) cameraManager.destroy()
//        if (::audioManager.isInitialized) audioManager.release()
        if (::motionDetector.isInitialized) {
            motionDetector.stop(); motionDetector.release()
        }
//        if (::proximityManager.isInitialized) proximityManager.stop()

        wakeLock?.release()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}