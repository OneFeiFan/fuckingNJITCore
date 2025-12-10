package com.feifan.fuckingnjit.utils.database

import com.feifan.fuckingnjit.model.Base
import io.objectbox.Box

object BaseDataBoxUtils : BaseBoxUtils() {
    override fun getDatabaseName() = "BASE"
    private val defaultUuid = 1L // 固定使用ID=1的对象
    private val baseBox: Box<Base> by lazy {
        val box = getBox(Base::class.java)
        if (box.count() == 0L) {
            box.put(Base().apply { uuid = defaultUuid })
        }
        box // 确保返回 Box<BaseData> 对象
    }

    // 获取单个 BaseData 对象
    fun getBaseData(): Base {
        var data = baseBox.get(defaultUuid)
        if (data == null) {
            data = Base().apply { uuid = defaultUuid }
            baseBox.put(data)
        }
        return data
    }

    fun getCurrentUserId(): String = getBaseData().currentUserId

    fun getStorePassword(): Boolean = getBaseData().storePassword

    fun getSemesterStartDate(): Long = getBaseData().semesterStartDate

    fun getCurrentWeek(): Int = getBaseData().currentWeek

    fun getWifiAuthTupe(): String = getBaseData().wifiAuthTupe ?: ""

    fun getSmartUpdate(): Boolean = getBaseData().smartUpdate ?: true

    // 更新 BaseData 对象
    fun updateBaseData(updater: (Base) -> Unit) {
        val data = getBaseData()
        updater(data)
        baseBox.put(data)
    }

}