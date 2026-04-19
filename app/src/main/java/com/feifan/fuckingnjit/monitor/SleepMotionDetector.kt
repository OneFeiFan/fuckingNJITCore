package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

class SleepMotionDetector(context: Context) : SensorEventListener {

    private val TAG = "SleepMotionDetector"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // 滤波参数
    private val alpha = 0.8f
    private val gravity = floatArrayOf(0f, 0f, 0f)

    // 计算变量（增加 Mutex 保证多线程采样安全）
    private val calculationLock = Mutex()
    private var sumSquares: Double = 0.0
    private var sampleCount: Int = 0

    @Volatile
    private var isSampling = false

    /**
     * 核心方法：单次采样触发
     * @param durationMs 采样窗口长度（如 300ms 或 1000ms）
     * @return 该时段内的运动能量分数
     */
    suspend fun captureEnergyScore(durationMs: Long): Double = calculationLock.withLock {
        if (accelerometer == null) return 1.0

        resetInternalState()

        try {
            isSampling = true
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)

            // 2. 等待采样窗口结束
            delay(durationMs)

        } catch (e: Exception) {
            Log.e(TAG, "采样过程中出现异常", e)
        } finally {
            // 3. 无论如何，一定要关闭监听以省电
            stopInternal()
        }

        // 4. 计算并返回结果
        return calculateRmsScore()
    }

    private fun resetInternalState() {
        sumSquares = 0.0
        sampleCount = 0
    }

    private fun stopInternal() {
        isSampling = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // 如果不在采样周期内，即便有残留回调也直接丢弃
        if (!isSampling || event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 经典高通滤波：分离重力
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z

        // 提取动态分量
        val vx = x - gravity[0]
        val vy = y - gravity[1]
        val vz = z - gravity[2]

        // 累加平方和（这里不分频率，进来多少算多少）
        sumSquares += (vx * vx + vy * vy + vz * vz).toDouble()
        sampleCount++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateRmsScore(): Double {
        if (sampleCount == 0) {
            Log.w(TAG, "采样周期内未接收到任何数据")
            return 1.0
        }

        val meanSquare = sumSquares / sampleCount
        val score = sqrt(meanSquare) + 1.0

        Log.d(TAG, "采样结束: 样本数=$sampleCount, 能量分数=$score")
        return score
    }

    fun release() {
        stopInternal()
    }
}