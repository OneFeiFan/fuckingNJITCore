//package com.feifan.keepalive
//
//import android.content.Context
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//
//class ProximityManager(context: Context) : SensorEventListener {
//    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//    private val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
//
//    @Volatile var isCovered = false
//        private set
//
//    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } }
//    fun stop() { sm.unregisterListener(this) }
//
//    override fun onSensorChanged(event: SensorEvent?) {
//        event?.let {
//            val dist = it.values[0]
//            val max = it.sensor.maximumRange
//            isCovered = dist < 1.0f || (max > 0 && dist < max && dist < 5.0f)
//        }
//    }
//    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
//}