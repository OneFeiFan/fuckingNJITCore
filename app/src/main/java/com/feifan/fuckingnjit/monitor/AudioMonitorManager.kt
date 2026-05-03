package com.feifan.fuckingnjit.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 音频监控管理器
 *
 * 负责采集环境噪音快照（以负数 dBFS 值返回），
 * 用于睡眠质量评估中的环境噪声检测。
 * 内置麦克风占用检测，当电话或其它应用抢占麦克风时自动让出资源。
 *
 * @param context 应用上下文
 */
class AudioMonitorManager(private val context: Context) {

    private val TAG = "AudioMonitorManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioRecord: AudioRecord? = null
    private var audioBuffer: ShortArray? = null
    private var recordBufferSize = 0

    @Volatile
    var isMicrophoneOccupied = false
        private set

    private var audioRecordingCallback: AudioManager.AudioRecordingCallback? = null

    /**
     * 初始化音频监控器
     *
     * 注册音频策略回调用于麦克风占用检测，
     * 并在权限允许的情况下提前预热 AudioRecord 实例建立长连接。
     */
    fun init() {
        registerAudioPolicyCallback()

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                ensureAudioRecordInit()
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord 预热失败，将在心跳时重试", e)
            }
        }
    }

    /**
     * 获取环境噪音快照
     *
     * 在指定时间窗口内录制音频并计算 RMS 值后转换为 dBFS。
     * 麦克风被占用或缺少权限时返回 -1.0。
     *
     * @param durationMs 采样窗口时长（毫秒），默认 250ms
     * @return 负数 dBFS 值；被中断或失败时返回 -1.0
     */
    suspend fun captureSnapshot(durationMs: Long = 250L): Double = withContext(Dispatchers.IO) {
        if (isMicrophoneOccupied || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext -1.0
        }

        try {
            ensureAudioRecordInit()

            // 如果实例被系统干掉或者状态损坏，直接丢弃重建
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED || isMicrophoneOccupied) {
                Log.w(TAG, "AudioRecord 状态异常，触发销毁重建")
                forceRelease()
                return@withContext -1.0
            }

            audioRecord?.startRecording()

            // startRecording 是耗时操作，再次校验期间是否被高优业务（电话）抢占
            if (isMicrophoneOccupied || audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
                return@withContext -1.0
            }

            val buffer = audioBuffer!!
            val startTime = System.currentTimeMillis()
            var totalSquaredSum = 0.0
            var totalSamples: Long = 0

            while (System.currentTimeMillis() - startTime < durationMs) {
                if (isMicrophoneOccupied) break // 被电话/微信打断，立即跳出

                val readSize = audioRecord?.read(buffer, 0, recordBufferSize) ?: -1
                if (readSize > 0) {
                    totalSamples += readSize
                    for (i in 0 until readSize) {
                        val sample = buffer[i]
                        totalSquaredSum += sample * sample
                    }
                } else {
                    break
                }
            }

            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    // 采样完毕后只执行 stop 挂起.保留实例
                    audioRecord?.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio stop error", e)
            }

            // 返回计算的负数 dBFS，如果被打断则返回 -1.0
            return@withContext if (totalSamples > 0 && !isMicrophoneOccupied) {
                val rms = sqrt(totalSquaredSum / totalSamples)
                if (rms > 0) 20 * log10(rms / 32767) else -1.0
            } else {
                -1.0
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 只有在发生底层抛错时（如死锁），才执行重量级的 forceRelease 销毁实例
            Log.e(TAG, "Noise snapshot failed, triggering self-healing", e)
            forceRelease()
            return@withContext -1.0
        }
    }

    /**
     * 确保 AudioRecord 实例已初始化
     *
     * 使用 8kHz 单声道 16bit 采样配置以降低功耗，
     * 数据源选用 UNPROCESSED 以获取最纯净的环境底噪。
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun ensureAudioRecordInit() {
        if (audioRecord == null) {
            val sampleRate = 8000 // 低采样率省电
            if (recordBufferSize == 0) {
                recordBufferSize = maxOf(
                    8192,
                    AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                )
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED, // 获取最纯净底噪，无视系统降噪
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recordBufferSize
            )

            if (audioBuffer == null || audioBuffer!!.size != recordBufferSize) {
                audioBuffer = ShortArray(recordBufferSize)
            }
        }
    }

    /**
     * 注册音频录制策略回调
     *
     * 监听系统音频录制配置变化，检测电话或其它应用是否占用了麦克风，
     * 占用时自动停止本实例的录音以避免冲突。
     */
    private fun registerAudioPolicyCallback() {
        audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
            @RequiresPermission(Manifest.permission.RECORD_AUDIO)
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                super.onRecordingConfigChanged(configs)
                val mode = audioManager.mode // 获取AudioManager状态，判断是否占用
                val isInCall =
                    mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION

                if (audioRecord == null) {
                    try {
                        ensureAudioRecordInit()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val mySessionId = audioRecord?.audioSessionId ?: -1
                var isOtherRecording = false // 其它占用

                if (configs.isNotEmpty()) {
                    isOtherRecording =
                        if (mySessionId == -1) true else configs.any { it.clientAudioSessionId != mySessionId }
                }

                val occupied = isInCall || isOtherRecording
                if (isMicrophoneOccupied != occupied) {
                    isMicrophoneOccupied = occupied
                    if (isMicrophoneOccupied) {
                        try {
                            audioRecord?.stop() //被占用就停止
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        audioManager.registerAudioRecordingCallback(audioRecordingCallback!!, null)
    }

    /**
     * 强制释放 AudioRecord 资源
     */
    private fun forceRelease() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

    /**
     * 释放所有音频相关资源并注销回调
     */
    fun release() {
        audioRecordingCallback?.let {
            try {
                audioManager.unregisterAudioRecordingCallback(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        forceRelease()
    }
}