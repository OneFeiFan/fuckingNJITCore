package com.feifan.fuckingnjit.utils.database

import android.content.Context
import androidx.startup.Initializer

class CoreInitProvider : Initializer<Unit> {
    override fun create(context: Context) {
        // 在这里执行初始化操作

        AppCategoryRepository.init(context)
        AppDataCenter.getBoxStore()?.let { store ->
            // 建议使用一个新的标记文件名，例如 "core_all_in_one_1.0.0"
            DbClearHelper.checkAndClear(context, store, "core_db_v1.0.0")
        }
    }

    // 声明依赖关系，确保在 WorkManager 之后初始化
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}