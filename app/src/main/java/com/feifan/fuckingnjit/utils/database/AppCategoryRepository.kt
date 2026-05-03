package com.feifan.fuckingnjit.utils.database

import android.content.Context
import android.util.Log
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.TypeReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object AppCategoryRepository {
    private const val TAG = "AppCategoryRepository"

    // 使用 ConcurrentHashMap 保证多线程读取安全性
    private val appCategoryMap = ConcurrentHashMap<String, String>()

    // 利用 Deferred 实现非阻塞式等待，null 表示还未开始初始化
    @Volatile
    private var initDeferred: CompletableDeferred<Unit>? = null

    @Synchronized
    fun init(context: Context) {
        // 如果正在初始化，或者已经成功初始化过了，直接跳过
        if (initDeferred != null && initDeferred?.isCompleted == false) {
            return
        }

        // 创建新的等待凭证
        initDeferred = CompletableDeferred()
        val appContext = context.applicationContext

        // 在 IO 线程池执行耗时加载，不阻塞主线程
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "正在独立加载应用分类数据库 (Fastjson)...")
                val startTime = System.currentTimeMillis()

                val jsonString = appContext.assets.open("app_mapping.json").bufferedReader().use {
                    it.readText()
                }

                val map = JSON.parseObject(
                    jsonString,
                    object : TypeReference<Map<String, String>>() {}
                )
                if (map != null) {
                    appCategoryMap.putAll(map)
                }

                Log.d(
                    TAG,
                    "数据库加载完成，共 ${appCategoryMap.size} 条，耗时: ${System.currentTimeMillis() - startTime}ms"
                )

                // 加载成功，唤醒所有正在挂起等待的调用方
                initDeferred?.complete(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "加载数据库失败", e)
                // 加载失败，通知调用方抛出异常，并清空状态以便后续重试
                initDeferred?.completeExceptionally(e)
                initDeferred = null
            }
        }
    }

    /**
     * 获取应用分类（挂起函数，可拓展性强）
     * 如果数据已加载完：瞬间返回。
     * 如果数据正在加载：挂起当前协程（不阻塞线程），等待加载完毕后返回。
     * 如果加载失败：内部消化异常，可在此处决定是返回默认值还是触发重新初始化。
     */
    suspend fun getCategory(context: Context, pkg: String): String? {
        // 如果调用方忘记 init，这里自动补救
        if (initDeferred == null) {
            init(context)
        }

        try {
            // 挂起等待，直到 initDeferred.complete() 被调用
            initDeferred?.await()
        } catch (e: Exception) {
            Log.e(TAG, "等待初始化失败，采用降级策略", e)
            return "异常" // 失败降级
        }

        return appCategoryMap[pkg]
    }
}