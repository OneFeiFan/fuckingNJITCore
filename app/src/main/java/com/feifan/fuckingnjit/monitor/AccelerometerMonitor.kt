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

class AccelerometerMonitor(context: Context) : SensorEventListener {

    private val TAG = "AccelerometerMonitor"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val alpha = 0.8f    // 滤波参数
    private val gravity = floatArrayOf(0f, 0f, 0f)

    private val calculationLock = Mutex() // 保证多线程采样安全
    private var sumSquares: Double = 0.0
    private var sampleCount: Int = 0

    @Volatile
    private var isSampling = false // 是否在采样

    //单次采样触发
    suspend fun captureEnergyScore(durationMs: Long): Double = calculationLock.withLock {
        if (accelerometer == null) return 1.0

        resetInternalState()

        try {
            isSampling = true
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)

            // 等待采样窗口结束
            delay(durationMs)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "采样过程中出现异常", e)
        } finally {
            // 无论如何，一定要关闭监听以省电
            stopInternal()
        }

        // 计算并返回结果
        return calculateRmsScore()
    }

    private fun resetInternalState() {
        sumSquares = 0.0
        sampleCount = 0
    }

    // 停止采样
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

        // 采用高通滤波分离重力
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

    //没有具体功能，只为了实现对象
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateRmsScore(): Double {
        if (sampleCount == 0) {
            Log.w(TAG, "采样周期内未接收到任何数据")
            return 1.0
        }

        val meanSquare = sumSquares / sampleCount
        val score = sqrt(meanSquare) + 1.0 //最少为1，防止出现无穷小数影响后续计算

        Log.d(TAG, "采样结束: 样本数=$sampleCount, 能量分数=$score")
        return score
    }

    fun release() {
        stopInternal()
    }
}