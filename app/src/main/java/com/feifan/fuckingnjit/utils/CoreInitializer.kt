package com.feifan.fuckingnjit.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.model.YiBan
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.YiBanBoxUtils
import com.feifan.yiban.Apis.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

object CoreInitializer {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    fun init() {
        try {
            Log.i("CoreInitializer", "Initializing Core")
            coroutineScope.launch {
                val week = withContext(Dispatchers.Default) {
                    val startTime =
                        LocalDate.parse(TimeManager.getInstance().getSemesterStartDate())
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    TimeManager.getInstance().calculateCurrentWeek(startTime)
                }
                withContext(Dispatchers.IO) {
                    BaseDataBoxUtils.updateBaseData { it.currentWeek = week }
                }
            }
        } catch (e: Exception) {
            println("init error: ${e.message}")
        }
    }

    suspend fun initYiBan(appContext: Context, mobile: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            return@withContext try {
                YiBanBoxUtils.init(appContext)

                var user = YiBanBoxUtils.getUserById(mobile) ?: YiBan(id = mobile, password = password)

                val yiBanTask = Task(appContext)
                val result = NetworkStatus.Success.toJsonResult(yiBanTask.init(user.id, user.password))

                YiBanBoxUtils.insertUser(user)
                user = YiBanBoxUtils.getUserById(mobile)!!
                BaseDataBoxUtils.updateBaseData { it.currentYiBanId = user.uuid }

                // 启用 KillYiBan 组件
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