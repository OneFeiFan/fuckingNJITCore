package com.feifan.fuckingnjit.utils

import android.content.Context
import com.feifan.fuckingnjit.database.BaseData
import com.feifan.fuckingnjit.database.MyObjectBox
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.File

object BaseDataBoxUtils {
    private lateinit var boxStore: BoxStore
    private lateinit var baseDataBox: Box<BaseData>
    private val defaultUuid = 1L // 固定使用ID=1的对象

    // 初始化必须在 Application 中调用
    fun init(context: Context) {
        boxStore = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .directory(File(context.filesDir, "BASE"))
            .build()
        baseDataBox = boxStore.boxFor(BaseData::class.java)
        // 初始化时检查，如果没有则创建
        if (baseDataBox.count() == 0L) {
            baseDataBox.put(BaseData().apply { uuid = defaultUuid })
        }
    }

    // 返回储存的 BoxStore 对象
    fun getBoxStore(): BoxStore = boxStore

    // 获取单个 BaseData 对象
    fun getBaseData(): BaseData {
        val data = baseDataBox.get(defaultUuid)
        if (data == null) {
            baseDataBox.put(BaseData().apply { uuid = defaultUuid })
        }
        return data
    }

    fun getCurrentUserId(): String = getBaseData().currentUserId

    fun getStorePassword(): Boolean = getBaseData().storePassword

    fun getSemesterStartDate(): Long = getBaseData().semesterStartDate

    fun getCurrentWeek(): Int = getBaseData().currentWeek

    // 更新 BaseData 对象
    fun updateBaseData(updater: (BaseData) -> Unit) {
        val data = getBaseData()
        updater(data)
        baseDataBox.put(data)
    }

    // 关闭数据库
    fun close() {
        boxStore.close()
    }
}
