package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.feifan.fuckingnjit.utils.database.AppDataCenter

object StepMonitorManager : SensorEventListener {

    private const val TAG = "StepMonitorManager"

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    // 直接通过数据中心获取今日步数，对外保持只读
    val currentSessionSteps: Int
        get() = AppDataCenter.getTodayRecord().currentSteps

    @Synchronized
    fun init(context: Context) {
        if (sensorManager != null) return

        val appContext = context.applicationContext

        // 注册传感器 (彻底移除了 的初始化逻辑)
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
            val rawTotalSteps = event.values[0]

            // 将所有逻辑委托给 AppDataCenter 安全的高阶函数，ObjectBox 的写入极速且线程安全
            AppDataCenter.updateTodayRecord { record ->

                // 1. 跨天自动重置，或者设备重启后的基准对齐
                if (record.lastRawSteps < 0 || rawTotalSteps < record.lastRawSteps) {
                    Log.w(TAG, "对齐计步器基准 (新基准: $rawTotalSteps)")
                    record.lastRawSteps = rawTotalSteps
                }

                // 2. 计算增量并累加
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

    @Synchronized
    fun release() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }
}