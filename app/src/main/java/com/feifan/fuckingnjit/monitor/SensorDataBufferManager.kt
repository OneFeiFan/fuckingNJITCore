package com.feifan.fuckingnjit.monitor

import com.feifan.fuckingnjit.model.SleepSensorRecord
import com.feifan.fuckingnjit.service.DataSyncService
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 传感器数据缓冲管理器
 *
 * 以内存缓冲区批量收集睡眠传感器数据点，达到阈值后异步刷入本地数据库。
 * 同时在每天上午11点的心跳周期中触发自动上传与数据清理。
 */
object SensorDataBufferManager {

    private val buffer = mutableListOf<SleepSensorRecord>()

    /** 批量写入数据库的阈值，对应约5分钟的采集量 */
    private const val BATCH_SIZE = 5

    /** 上次自动上传的年中日，用于保证每天只触发一次 */
    private var lastAutoUploadDayOfYear = -1

    /**
     * 向缓冲区添加一条传感器记录
     *
     * 写入后检查是否达到批量写入阈值或是否需要触发每日自动上传。
     *
     * @param data 睡眠综合得分数据
     */
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
     *
     * 采用拷贝-清空策略：先复制当前快照再清空原集合，
     * 数据库 IO 操作在独立协程中执行以避免阻塞调用方。
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
                AppDataCenter.insertSensorBatch(recordsToSave)
            } catch (e: Exception) {
                e.printStackTrace()
                println("异常：${e.message}")
            }
        }
    }

    /**
     * 检查并尝试触发每日自动上传
     *
     * 在当天上午 11:00 ~ 11:59 的任意一次心跳中触发一次，
     * 触发后调用 [DataSyncService] 完成数据上传与清理。
     */
    private fun checkAndTriggerAutoUpload() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentDay = calendar.get(Calendar.DAY_OF_YEAR)

        // 设定在每天上午 11 点后触发 (11:00 ~ 11:59 的任意心跳)
        // 并且确保今天还没有成功触发过
        if (currentHour == 11 && currentDay != lastAutoUploadDayOfYear) {
            lastAutoUploadDayOfYear = currentDay

            CoroutineScope(Dispatchers.IO).launch {
                println("⏰ 触发心跳机制：每天 11 点边缘计算数据修剪与自动上传")
                DataSyncService.uploadAndClearData()
            }
        }
    }
}