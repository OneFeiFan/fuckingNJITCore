package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class SleepMotionDetector(context: Context) : SensorEventListener {

    private val TAG = "SleepMotionDetector"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // 记录最近 3 次 FIFO 硬件刷新的间隔(ms)
    private val flushIntervals = ArrayDeque<Long>(3)
    private var lastFlushWallClockTime = System.currentTimeMillis()

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

        // 批处理上报时，瞬间会涌入多条 event，只计算批与批之间的宏观间隔
        val delta = now - lastFlushWallClockTime
        if (delta > 2000) {
            if (flushIntervals.size >= 3) {
                flushIntervals.removeFirst()
            }
            flushIntervals.addLast(delta)
            lastFlushWallClockTime = now

            // 维持后端的波形判定基数
            event?.let {
                currentMotionScore = Math.abs(it.values[0].toDouble()) + Math.abs(it.values[1].toDouble()) + Math.abs(it.values[2].toDouble())
            }

            Log.d(TAG, "硬件 FIFO 刷新. 间隔: ${delta}ms. 队列: $flushIntervals")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 分析上传频率，判断用户是否处于活跃（清醒/运动）状态
     */
    fun isUserActive(): Boolean {
        // 数据不足时保守判定为活跃
        if (flushIntervals.size < 3) return true

        val avgInterval = flushIntervals.average()
        // 理想延迟是 60秒。如果平均刷新间隔 < 45秒，说明 FIFO 被运动数据提前塞满了。
        return avgInterval < 45_000
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