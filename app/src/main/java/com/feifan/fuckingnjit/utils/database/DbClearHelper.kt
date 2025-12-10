package com.feifan.fuckingnjit.utils.database

import android.content.Context
import io.objectbox.BoxStore
import java.io.File
import java.io.IOException

object DbClearHelper {

    fun checkAndClear(context: Context, boxStore: BoxStore,fileName:String) {
        // 1. 定位标记文件路径 (在 /data/user/0/包名/files/ 目录下)
        val markerFile = File(context.filesDir, fileName)

        // 2. 如果文件存在，说明已经清空过了，直接返回
        if (markerFile.exists()) {
            return
        }

        try {
            // 3. 执行 ObjectBox 清空
            boxStore.removeAllObjects()
            android.util.Log.i("DB_CLEANER", "数据库已清空")

            // 4. 创建标记文件，确保下次不再触发
            markerFile.createNewFile()

        } catch (e: IOException) {
            e.printStackTrace()
            // 如果创建文件失败，可能导致下次重复清空，需要注意权限或存储空间
        } catch (e: Exception) {
            e.printStackTrace()
            // 数据库操作异常
        }
    }
}