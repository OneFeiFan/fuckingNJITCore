package com.feifan.fuckingnjit.utils.database

import android.content.Context
import androidx.startup.Initializer

// 采用了安卓程序的特殊初始化对象，在程序启动早期完成初始化
class CoreInitProvider : Initializer<Unit> {
    override fun create(context: Context) {
        // 在这里执行初始化操作
        AppCategoryRepository.init(context)
        // 用于清空数据库的例子
//        AppDataCenter.getBoxStore()?.let { store ->
//            DbClearHelper.checkAndClear(context, store, "core_db_v1.0.0")
//        }
    }

    // 这个方法用于声明依赖关系，确保在 xxx初始化之后初再始化CoreInitProvider
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList() // 本软件没有这种依赖关系
    }
}