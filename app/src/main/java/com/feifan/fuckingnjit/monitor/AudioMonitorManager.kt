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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

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

    fun init() {
        registerAudioPolicyCallback()
    }

    /**
     * 获取环境噪音快照
     * @param durationMs 录音时长(毫秒)
     * @return 环境分贝值(dBFS)。如果麦克风被占用或无权限，返回 -1.0
     */
    suspend fun captureSnapshot(durationMs: Long = 250L): Double = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext -1.0
        }
        if (isMicrophoneOccupied) {
            Log.d(TAG, "麦克风被其他应用占用，跳过快照。")
            return@withContext -1.0
        }

        try {
            ensureAudioRecordInit()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED || isMicrophoneOccupied) {
                return@withContext -1.0
            }

            audioRecord?.startRecording()

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
            Log.e(TAG, "Noise snapshot failed", e)
            forceRelease()
            return@withContext -1.0
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun ensureAudioRecordInit() {
        if (audioRecord == null) {
            val sampleRate = 8000 // 低采样率省电
            if (recordBufferSize == 0) {
                recordBufferSize = maxOf(
                    8192,
                    AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
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

    private fun registerAudioPolicyCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioRecordingCallback = object : AudioManager.AudioRecordingCallback() {
                @RequiresPermission(Manifest.permission.RECORD_AUDIO)
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    super.onRecordingConfigChanged(configs)
                    val mode = audioManager.mode
                    val isInCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION

                    if(audioRecord == null) {
                        try { ensureAudioRecordInit() } catch (e: Exception) {}
                    }

                    val mySessionId = audioRecord?.audioSessionId ?: -1
                    var isOtherRecording = false

                    if (configs.isNotEmpty()) {
                        isOtherRecording = if (mySessionId == -1) true else configs.any { it.clientAudioSessionId != mySessionId }
                    }

                    val occupied = isInCall || isOtherRecording
                    if (isMicrophoneOccupied != occupied) {
                        isMicrophoneOccupied = occupied
                        if (isMicrophoneOccupied) {
                            try { audioRecord?.stop() } catch (e: Exception) {}
                        }
                    }
                }
            }
            audioManager.registerAudioRecordingCallback(audioRecordingCallback!!, null)
        }
    }

    private fun forceRelease() {
        try { audioRecord?.release() } catch (e: Exception) {}
        audioRecord = null
    }

    fun release() {
        audioRecordingCallback?.let {
            try { audioManager.unregisterAudioRecordingCallback(it) } catch (e: Exception) {}
        }
        forceRelease()
    }
}