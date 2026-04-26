package com.feifan.fuckingnjit.utils.database

import android.content.Context
import androidx.startup.Initializer

class CoreInitProvider : Initializer<Unit> {
    override fun create(context: Context) {
        // 在这里执行初始化操作
        UserBoxUtils.init(context)
        BaseDataBoxUtils.init(context)
        SleepRecordBoxUtils.init(context)
        SleepSensorBoxUtils.init(context)
        ClassFocusRecordBoxUtils.init(context)

        DbClearHelper.checkAndClear(context, UserBoxUtils.getBoxStore()!!, "user_1.2.5")
        DbClearHelper.checkAndClear(context, BaseDataBoxUtils.getBoxStore()!!, "base_1.2.5")
        AppCategoryRepository.init(context)
    }

    // 声明依赖关系，确保在 WorkManager 之后初始化
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}