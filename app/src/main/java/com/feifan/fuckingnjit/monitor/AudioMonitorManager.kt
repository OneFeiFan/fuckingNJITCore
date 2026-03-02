package com.feifan.fuckingnjit.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt

class AudioMonitorManager(private val context: Context) {

    private val TAG = "AudioMonitorManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // 录音相关
    private var audioRecord: AudioRecord? = null
    private var audioBuffer: ShortArray? = null
    private var recordBufferSize = 0

    // 状态
    var lastNoiseDb = 0.0
        private set

    @Volatile
    var isMicrophoneOccupied = false
        private set

    private var audioRecordingCallback: AudioManager.AudioRecordingCallback? = null

    fun init() {
        registerAudioPolicyCallback()
    }

    /**
     * 执行一次噪音检测 (由 Service 5秒调用一次)
     */
    fun detectNoise() {
        // 权限检查
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            lastNoiseDb = -1.0
            return
        }

        // 占用检查
        if (isMicrophoneOccupied) {
            lastNoiseDb = 0.0
            return
        }

        try {
            ensureAudioRecordInit()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                lastNoiseDb = -1.0
                return
            }

            audioRecord?.startRecording()

            val buffer = audioBuffer!!
            val startTime = System.currentTimeMillis()
            var totalSquaredSum = 0.0
            var totalSamples: Long = 0

            // 录制约 1 秒 (注意：这是阻塞的，会占用 Service 线程约 1s)
            // 在 5s 心跳架构下，这是可以接受的
            while (System.currentTimeMillis() - startTime < 300) {
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
                    audioRecord?.stop()
                }
            } catch (e: Exception) {}

            if (totalSamples > 0) {
                val rms = sqrt(totalSquaredSum / totalSamples)
                if (rms > 0) {
                    lastNoiseDb = 20 * log10(rms / 32767)
                }
            } else {
                lastNoiseDb = -1.0
            }

        } catch (e: Exception) {
            Log.e(TAG, "Noise detect failed", e)
            e.printStackTrace()
            forceRelease()
            lastNoiseDb = -1.0
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun ensureAudioRecordInit() {
        if (audioRecord == null) {
            val sampleRate = 8000
            if (recordBufferSize == 0) {
                recordBufferSize = maxOf(8192, AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED,
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

    private fun registerAudioPolicyCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    super.onRecordingConfigChanged(configs)
                    val mode = audioManager.mode
                    val isInCall = mode == AudioManager.MODE_IN_CALL ||
                            mode == AudioManager.MODE_IN_COMMUNICATION

                    val mySessionId = audioRecord?.audioSessionId ?: -1
                    var isOtherRecording = false

                    if (configs.isNotEmpty()) {
                        if (mySessionId == -1) {
                            isOtherRecording = true
                        } else {
                            isOtherRecording = configs.any { it.clientAudioSessionId != mySessionId }
                        }
                    }

                    val occupied = isInCall || isOtherRecording
                    if (isMicrophoneOccupied != occupied) {
                        isMicrophoneOccupied = occupied
//                        Log.d(TAG, "Mic Occupied: $occupied")
                        if (isMicrophoneOccupied) {
                            // 被抢占，立即停止当前录音
                            try { audioRecord?.stop() } catch (e: Exception) {}
                        }
                    }
                }
            }
            audioManager.registerAudioRecordingCallback(audioRecordingCallback!!, null)
        }
    }

    private fun forceRelease() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
    }

    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioRecordingCallback != null) {
            try { audioManager.unregisterAudioRecordingCallback(audioRecordingCallback!!) } catch (e: Exception) {}
        }
        forceRelease()
    }
}