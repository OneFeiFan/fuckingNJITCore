package com.feifan.fuckingnjit.utils.database

import android.content.Context
import android.util.Log
import io.objectbox.BoxStore
import java.io.File

/**
 * 数据库一次性清空工具，通过标记文件机制确保 ObjectBox 数据仅被清除一次。
 *
 * 适用于版本升级或首次安装时需要重置本地数据的场景。
 */
@Suppress("unused")
object DbClearHelper {

    /**
     * 检查标记文件是否存在：若不存在则清空 ObjectBox 全部数据并创建标记文件，
     * 若已存在则跳过，避免重复清空。
     *
     * @param context   应用上下文，用于定位内部存储目录
     * @param boxStore  目标 ObjectBox 存储实例
     * @param fileName  标记文件名，不同场景应使用不同的文件名以示区分
     */
    fun checkAndClear(context: Context, boxStore: BoxStore, fileName: String) {
        // 定位标记文件路径 在：/data/user/0/包名/files/目录下
        val markerFile = File(context.filesDir, fileName)

        // 如果文件存在，说明已经清空过了，直接返回
        if (markerFile.exists()) {
            return
        }

        try {
            boxStore.removeAllObjects()
            Log.i("DB_CLEANER", "数据库已清空")

            // 创建标记文件，确保下次不再触发
            markerFile.createNewFile()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}