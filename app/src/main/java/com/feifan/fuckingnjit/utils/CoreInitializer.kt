package com.feifan.fuckingnjit.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.yiban.Apis.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CoreInitializer {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    fun init() {
        try {
            Log.i("CoreInitializer", "Initializing Core")
            coroutineScope.launch {
                // 直接从 AppDataCenter 获取已存储的毫秒级时间戳，省去 LocalDate 解析步骤
                val startMs = AppDataCenter.getSystemConfig().semesterStartDateMs

                if (startMs > 0L) {
                    val week = withContext(Dispatchers.Default) {
                        EduScheduleConfig.calculateCurrentWeek(startMs)
                    }
                    withContext(Dispatchers.IO) {
                        AppDataCenter.updateSystemConfig { it.currentWeek = week }
                    }
                }
            }
        } catch (e: Exception) {
            println("init error: ${e.message}")
        }
    }

    suspend fun initYiBan(appContext: Context, mobile: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // 1. 易班账号必须依附于教务账号存在
                val currentUser = AppDataCenter.getCurrentUser()
                    ?: throw Exception("请先登录教务系统账号")

                // 2. 初始化并验证易班账号 (此时不查库，直接用传入的参数去验证)
                val yiBanTask = Task(appContext)
                val result = NetworkStatus.Success.toJsonResult(yiBanTask.init(mobile, password))

                // 3. 验证成功后，将易班凭据直接合并到当前 User 实体中，彻底消灭 YiBan 实体表
                currentUser.yibanId = mobile
                currentUser.yibanPassword = password
                AppDataCenter.saveUser(currentUser)

                // (注意：由于易班跟 User 绑定了，AppSystem.currentYiBanId 也被彻底废弃了)

                // 4. 启用 KillYiBan 组件
                val packageManager = appContext.packageManager
                val componentName = ComponentName(appContext, KillYiBan::class.java)
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                result
            } catch (e: Exception) {
                SystemActionHelper.handleException(appContext, e, "初始化易班失败")
                NetworkStatus.UnknownError.toJsonResult(e.message)
            }
        }
}