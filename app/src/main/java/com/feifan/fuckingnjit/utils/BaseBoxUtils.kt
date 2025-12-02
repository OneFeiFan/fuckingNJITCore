package com.feifan.fuckingnjit.utils

import android.content.Context
import com.feifan.fuckingnjit.model.MyObjectBox
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.File

abstract class BaseBoxUtils {
    // 建议改用抽象类控制初始化流程
    @Volatile
    private var boxStore: BoxStore? = null

    abstract fun getDatabaseName(): String

    // 线程安全的惰性初始化
    fun init(context: Context) {
        if (boxStore == null) {
            synchronized(this) {
                if (boxStore == null) {
                    boxStore = MyObjectBox.builder()
                        .androidContext(context.applicationContext) // 始终使用Application Context
                        .directory(File(context.filesDir, getDatabaseName()))
                        .build()
                }
            }
        }
    }

    // 获取安全的Box实例
    fun <T> getBox(clazz: Class<T>): Box<T> {
        return boxStore?.boxFor(clazz)
            ?: throw IllegalStateException("BoxStore not initialized. Call init() first")
    }

    fun isInitialized() = boxStore != null
}