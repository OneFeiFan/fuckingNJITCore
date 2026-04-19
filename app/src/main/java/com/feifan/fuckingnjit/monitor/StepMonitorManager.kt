package com.feifan.fuckingnjit.monitor

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.Calendar

object StepMonitorManager : SensorEventListener {

    private const val TAG = "StepMonitorManager"
    private const val PREFS_NAME = "step_monitor_prefs"

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var prefs: SharedPreferences? = null

    private var rawTotalSteps: Float = -1f
    private var lastRawSteps: Float = -1f
    private var lastRecordedDayOfYear = -1

    var currentSessionSteps: Int = 0
        get() {
            checkAndResetIfNewDay()
            return field
        }
        private set

    @Synchronized
    fun init(context: Context) {
        if (sensorManager != null) return

        val appContext = context.applicationContext
        // 1. 初始化本地持久化存储
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 2. 从硬盘恢复上次 App 死亡前的数据
        lastRecordedDayOfYear = prefs?.getInt("last_day", -1) ?: -1
        lastRawSteps = prefs?.getFloat("last_raw", -1f) ?: -1f
        currentSessionSteps = prefs?.getInt("current_steps", 0) ?: 0


        // 3. 注册传感器
        sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor != null) {
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "计步器传感器初始化成功")
        } else {
            Log.e(TAG, "该设备不支持计步器硬件传感器")
        }
    }

    private fun checkAndResetIfNewDay() {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        if (lastRecordedDayOfYear == -1) {
            lastRecordedDayOfYear = currentDay
            saveToDisk() // 存盘
            return
        }

        if (lastRecordedDayOfYear != currentDay) {
            Log.d(TAG, "检测到跨天，今日步数清零")
            currentSessionSteps = 0
            lastRecordedDayOfYear = currentDay
            saveToDisk() // 存盘
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            checkAndResetIfNewDay()

            rawTotalSteps = event.values[0]

            if (lastRawSteps < 0) {
                lastRawSteps = rawTotalSteps
                saveToDisk()
            }

            if (rawTotalSteps < lastRawSteps) {
                Log.w(TAG, "检测到设备硬件重启，重新对齐计步器基准")
                lastRawSteps = rawTotalSteps
                saveToDisk()
            }

            val delta = (rawTotalSteps - lastRawSteps).toInt()
            if (delta > 0) {
                currentSessionSteps += delta
                lastRawSteps = rawTotalSteps
                Log.d(TAG, "步数增加: +$delta, 今日累计: $currentSessionSteps")

                // 每次有实质性增加时，异步写入硬盘
                saveToDisk()
            }
        }
    }

    /**
     * 将关键状态写入硬盘，防止被杀后台丢失
     */
    private fun saveToDisk() {
        // 使用 apply() 是异步非阻塞的，不会卡顿传感器回调线程
        prefs?.edit()?.apply {
            putInt("last_day", lastRecordedDayOfYear)
            putFloat("last_raw", lastRawSteps)
            putInt("current_steps", currentSessionSteps)
            apply()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Synchronized
    fun release() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
    }
}