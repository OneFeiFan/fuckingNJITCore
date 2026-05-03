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

/**
 * 加速度传感器能量采集器
 *
 * 通过高通滤波分离重力分量后计算 RMS（均方根）能量值，
 * 用于睡眠质量评估中的体动检测。采样过程使用协程挂起，
 * 采样窗口结束后自动释放传感器监听以节省电量。
 *
 * @param context 应用上下文，用于获取 SensorManager 服务
 */
class AccelerometerMonitor(context: Context) : SensorEventListener {

    private val TAG = "AccelerometerMonitor"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** 高通滤波参数，值越大重力跟踪越平滑 */
    private val alpha = 0.8f
    private val gravity = floatArrayOf(0f, 0f, 0f)

    /** 互斥锁，保证多线程采样安全 */
    private val calculationLock = Mutex()
    private var sumSquares: Double = 0.0
    private var sampleCount: Int = 0

    @Volatile
    private var isSampling = false

    /**
     * 执行单次能量采样
     *
     * 在指定时间窗口内注册加速度传感器监听，收集原始数据并计算 RMS 能量分数。
     * 采样结束后自动注销监听器。
     *
     * @param durationMs 采样窗口时长（毫秒）
     * @return RMS 能量分数，最低返回 1.0 以避免极小值干扰后续计算；无传感器时返回 1.0
     */
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
            // 无论如何都要关闭监听以省电
            stopInternal()
        }

        return calculateRmsScore()
    }

    /**
     * 重置内部累计状态
     */
    private fun resetInternalState() {
        sumSquares = 0.0
        sampleCount = 0
    }

    /**
     * 停止采样并注销传感器监听
     */
    private fun stopInternal() {
        isSampling = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // 不在采样周期内的残留回调直接丢弃
        if (!isSampling || event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 采用低通滤波提取重力分量
        gravity[0] = alpha * gravity[0] + (1 - alpha) * x
        gravity[1] = alpha * gravity[1] + (1 - alpha) * y
        gravity[2] = alpha * gravity[2] + (1 - alpha) * z

        // 高通滤波得到动态分量（去除重力影响）
        val vx = x - gravity[0]
        val vy = y - gravity[1]
        val vz = z - gravity[2]

        // 累加平方和用于 RMS 计算
        sumSquares += (vx * vx + vy * vy + vz * vz).toDouble()
        sampleCount++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 计算采样周期内的 RMS 能量分数
     *
     * @return 能量分数，结果加 1.0 作为下界保护
     */
    private fun calculateRmsScore(): Double {
        if (sampleCount == 0) {
            Log.w(TAG, "采样周期内未接收到任何数据")
            return 1.0
        }

        val meanSquare = sumSquares / sampleCount
        val score = sqrt(meanSquare) + 1.0 // 下界保护，防止出现无穷小数影响后续计算

        Log.d(TAG, "采样结束: 样本数=$sampleCount, 能量分数=$score")
        return score
    }

    /**
     * 释放资源并停止所有监听
     */
    fun release() {
        stopInternal()
    }
}