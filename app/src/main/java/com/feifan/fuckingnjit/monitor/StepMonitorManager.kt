package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.feifan.fuckingnjit.utils.database.AppDataCenter

/**
 * 计步器监控管理器
 *
 * 基于硬件 TYPE_STEP_COUNTER 传感器实现计步功能，
 * 采用增量累加策略将传感器原始值转化为今日累计步数并持久化到数据库。
 * 支持跨天自动重置和设备重启后的基准对齐。
 */
object StepMonitorManager : SensorEventListener {

    private const val TAG = "StepMonitorManager"

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    /**
     * 当前会话的今日累计步数（只读）
     *
     * 直接从数据中心读取，每次取值均为最新持久化结果。
     */
    val currentSessionSteps: Int
        get() = AppDataCenter.getTodayRecord().currentSteps

    /**
     * 初始化计步器监听
     *
     * 重复调用时直接跳过。若设备不支持硬件计步传感器则打印错误日志。
     *
     * @param context 应用上下文
     */
    @Synchronized
    fun init(context: Context) {
        if (sensorManager != null) return

        val appContext = context.applicationContext

        // 注册传感器
        sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor != null) {
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "计步器传感器初始化成功")
        } else {
            Log.e(TAG, "该设备不支持计步器硬件传感器")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val rawTotalSteps = event.values[0]// 传感器里的原始数据

            AppDataCenter.updateTodayRecord { record ->

                // 跨天自动重置，或者设备重启后进行基准对齐
                if (record.lastRawSteps < 0 || rawTotalSteps < record.lastRawSteps) {
                    Log.w(TAG, "对齐计步器基准 (新基准: $rawTotalSteps)")
                    record.lastRawSteps = rawTotalSteps
                }

                // 计算增量并累加
                val delta = (rawTotalSteps - record.lastRawSteps).toInt()
                if (delta > 0) {
                    record.currentSteps += delta
                    record.lastRawSteps = rawTotalSteps
                    Log.d(TAG, "步数增加: +$delta, 今日累计: ${record.currentSteps}")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 注销传感器监听并释放资源
     */
    @Synchronized
    fun release() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }
}