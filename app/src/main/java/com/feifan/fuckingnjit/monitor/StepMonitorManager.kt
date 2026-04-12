package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * 步数监测全局单例管理器
 */
object StepMonitorManager : SensorEventListener {

    private const val TAG = "StepMonitorManager"
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    // 手机开机以来的总步数
    private var rawTotalSteps: Float = -1f

    // 供外部全局读取的“当前活跃步数”
    var currentSessionSteps: Int = 0
        private set

    // 记录上一次的步数，用于计算差值
    private var lastRawSteps: Float = -1f

    @Synchronized
    fun init(context: Context) {
        if (sensorManager != null) return // 避免重复初始化

        // 必须使用 applicationContext，防止单例持有 Service/Activity 导致内存泄漏
        sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor != null) {
            // 注册监听
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "计步器传感器初始化成功")
        } else {
            Log.e(TAG, "该设备不支持计步器硬件传感器")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            rawTotalSteps = event.values[0]

            if (lastRawSteps < 0) {
                // 第一次获取数据，设为基准
                lastRawSteps = rawTotalSteps
            }

            // 计算增量
            val delta = (rawTotalSteps - lastRawSteps).toInt()
            if (delta > 0) {
                currentSessionSteps += delta
                lastRawSteps = rawTotalSteps
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 计步器无需处理精度变化
    }

    @Synchronized
    fun release() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }
}