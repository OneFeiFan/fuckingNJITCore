package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.sqrt

class SleepMotionDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravity = floatArrayOf(0f, 0f, 0f)
    private val alpha = 0.8f

    // 在线计算变量
    private var sumSquares: Double = 0.0
    private var sampleCount: Int = 0

    @Volatile
    var currentMotionScore: Double = 0.0
        private set

    // 后台线程处理传感器数据
    private val workerThread = HandlerThread("MotionThread").apply { start() }
    private val handler = Handler(workerThread.looper)

    /**
     * 触发一次采样 (由 Service 调用)
     * 逻辑：开启 -> 延时1秒 -> 关闭并计算
     */
    suspend fun triggerSampleSync(): Double {
        // 重置
        sumSquares = 0.0
        sampleCount = 0

        accelerometer?.let {
            try {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
                delay(300)//主心跳采用了后台进程，所以这里直接改为阻塞实现
                stopInternal()
            } catch (e: Exception) {
                Log.e("SleepMotionDetector", "Register failed", e)
                e.printStackTrace()
            }
        }
        return currentMotionScore
    }

    private fun stopInternal() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
        }

        currentMotionScore = calculateScore()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            gravity[0] = alpha * gravity[0] + (1 - alpha) * x
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z

            val vx = x - gravity[0]
            val vy = y - gravity[1]
            val vz = z - gravity[2]

            val magnitudeSq = (vx * vx + vy * vy + vz * vz).toDouble()

            // 在线累加
            sumSquares += magnitudeSq
            sampleCount++
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateScore(): Double {
        if (sampleCount == 0) return 1.0
        val meanSquare = sumSquares / sampleCount
        val rms = sqrt(meanSquare)
        return rms + 1
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
        }
        workerThread.quitSafely()
    }

    fun stop() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
        }
    }
}