package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

class SleepMotionDetector(context: Context) : SensorEventListener {

    private val TAG = "SleepMotionDetector"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // 记录最近 3 次 FIFO 硬件刷新的间隔(ms)
    private val flushIntervals = ArrayDeque<Long>(3)
    private var lastFlushWallClockTime = System.currentTimeMillis()

    private val gravity = floatArrayOf(0f, 0f, 0f)
    private val alpha = 0.8f

    // 在线计算变量
    private var sumSquares: Double = 0.0
    private var sampleCount: Int = 0

    // 提供给后端计算公式的兼容性分数
    var currentMotionScore = 0.0
        private set

    fun start() {
        sensor?.let {
            // 设置 60 秒的底层硬件 FIFO 延迟 (60,000,000 微秒)
            val maxLatencyUs = 60_000_000
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, maxLatencyUs)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val now = System.currentTimeMillis()
        val delta = now - lastFlushWallClockTime

        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            // 【你最原始的滤波核心逻辑：一行未改】
            gravity[0] = alpha * gravity[0] + (1 - alpha) * x
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z

            val vx = x - gravity[0]
            val vy = y - gravity[1]
            val vz = z - gravity[2]

            val magnitudeSq = (vx * vx + vy * vy + vz * vz).toDouble()

            // 在线累加能量
            sumSquares += magnitudeSq
            sampleCount++
        }

        // 当底层硬件吐出一批数据，且批次间隔大于2秒时结算
        if (delta > 2000) {
            // 维护间隔队列
            if (flushIntervals.size >= 3) {
                flushIntervals.removeFirst()
            }
            flushIntervals.addLast(delta)
            lastFlushWallClockTime = now

            // 计算这一批次数据的平均运动能量，并更新给后端
            if (sampleCount > 0) {
                // 使用均方值作为最终分数（也可以根据你后端的需要加上 Math.sqrt）
                currentMotionScore = sumSquares / sampleCount
                // 结算后清零，等待下一批次
                sumSquares = 0.0
                sampleCount = 0
            }

            Log.d(TAG, "硬件 FIFO 刷新. 间隔: ${delta}ms. 队列: $flushIntervals")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 判定活跃状态：平均间隔 < 45秒 为活跃
     */
    fun isUserActive(): Boolean {
        if (flushIntervals.size < 3) return true
        return flushIntervals.average() < 45_000
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        flushIntervals.clear()
        currentMotionScore = 0.0
    }

    fun release() {
        stop()
    }
}