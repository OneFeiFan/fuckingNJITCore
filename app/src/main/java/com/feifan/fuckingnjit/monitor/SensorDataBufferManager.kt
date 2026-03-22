package com.feifan.fuckingnjit.monitor

import com.feifan.fuckingnjit.model.SleepSensorRecord
import com.feifan.fuckingnjit.utils.database.SleepSensorBoxUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SensorDataBufferManager {

    private val buffer = mutableListOf<SleepSensorRecord>()
    // 5秒一次心跳，60次 = 300秒 = 5分钟写入一次数据库
    private const val BATCH_SIZE = 60

    @Synchronized
    fun addRecord(data: Double) {
        val record = SleepSensorRecord(
            timestamp = System.currentTimeMillis(),
            mixdata = data
        )
        buffer.add(record)

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
            }
        }
    }
}