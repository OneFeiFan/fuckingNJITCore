package com.feifan.fuckingnjit.utils

import com.feifan.fuckingnjit.database.BaseData
import io.objectbox.Box

object BaseDataBoxUtils : BaseBoxUtils() {
    override fun getDatabaseName() = "BASE"
    private val defaultUuid = 1L // 固定使用ID=1的对象
    private val baseDataBox: Box<BaseData> by lazy {
        val box = getBox(BaseData::class.java)
        if (box.count() == 0L) {
            box.put(BaseData().apply { uuid = defaultUuid })
        }
        box // 确保返回 Box<BaseData> 对象
    }

    // 返回储存的 BoxStore 对象
    fun getBoxStore(): Box<BaseData> = baseDataBox

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

}
