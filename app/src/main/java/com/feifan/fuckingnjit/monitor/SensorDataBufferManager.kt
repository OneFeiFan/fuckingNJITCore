package com.feifan.fuckingnjit.monitor

import com.feifan.fuckingnjit.model.SleepSensorRecord
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.database.SleepSensorBoxUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object SensorDataBufferManager {

    private val buffer = mutableListOf<SleepSensorRecord>()

    // 一分钟一次心跳，5分钟写入一次数据库
    private const val BATCH_SIZE = 5

    private var lastAutoUploadDayOfYear = -1

    @Synchronized
    fun addRecord(data: Double) {
        val record = SleepSensorRecord(
            timestamp = System.currentTimeMillis(),
            mixdata = data
        )
        buffer.add(record)
        checkAndTriggerAutoUpload()
        if (buffer.size >= BATCH_SIZE) {
            flushToDatabase()
        }
    }

    /**
     * 将缓存数据异步刷入本地数据库
     */
    @Synchronized
    fun flushToDatabase() {
        if (buffer.isEmpty()) return

        // 拷贝一份当前数据，清空原集合继续接收新数据
        val recordsToSave = buffer.toList()
        buffer.clear()

        // 在 IO 线程池中执行数据库写入，绝不阻塞当前业务
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SleepSensorBoxUtils.insertBatch(recordsToSave)
            } catch (e: Exception) {
                e.printStackTrace()
                println("异常：${e.message}")
            }
        }
    }

    /**
     * 检查当前时间，并在接近中午 12 点（如 11 点）时触发自动修剪和上传
     */
    private fun checkAndTriggerAutoUpload() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)

        // 设定在每天上午 11 点触发 (11:00 ~ 11:59 的任意心跳)
        // 并且确保今天还没有成功触发过
        if (currentHour == 11 && currentDay != lastAutoUploadDayOfYear) {
            lastAutoUploadDayOfYear = currentDay

            CoroutineScope(Dispatchers.IO).launch {
                println("⏰ 触发心跳机制：每天 11 点边缘计算数据修剪与自动上传")
                Manager.uploadAndClearData()
            }
        }
    }
}