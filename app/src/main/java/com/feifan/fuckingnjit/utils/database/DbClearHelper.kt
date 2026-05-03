package com.feifan.fuckingnjit.utils.database

import android.content.Context
import android.util.Log
import io.objectbox.BoxStore
import java.io.File

object DbClearHelper {

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