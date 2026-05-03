package com.feifan.fuckingnjit.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.utils.academic.KillYiBan
import com.feifan.fuckingnjit.utils.database.AppDataCenter
import com.feifan.fuckingnjit.utils.network.NetworkStatus
import com.feifan.fuckingnjit.utils.system.SystemActionHelper
import com.feifan.yiban.Apis.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用核心初始化器
 *
 * 负责应用启动时的关键初始化逻辑：根据学期开始时间恢复当前教学周次，
 * 以及初始化易班账号并启用 KillYiBan 组件。
 */
@Suppress("unused")
object CoreInitializer {
//    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    /**
     * 初始化核心数据：根据已保存的学期开始时间计算并恢复当前周次
     */
    fun init() {
        try {
            Log.i("CoreInitializer", "Initializing Core")
//            coroutineScope.launch {
            // 获取开学时间
            val startMs = AppDataCenter.getSystemConfig().semesterStartDateMs

            if (startMs > 0L) {
                val week = EduScheduleConfig.calculateCurrentWeek(startMs) // 计算当前周
//                    withContext(Dispatchers.IO) {
                AppDataCenter.updateSystemConfig { it.currentWeek = week }
//                    }
            }
//            }
        } catch (e: Exception) {
            println("init error: ${e.message}")
        }
    }

    /**
     * 初始化易班账号并启用 KillYiBan 广播接收器
     *
     * 验证凭据后将易班信息合并到当前 User 实体，
     * 并通过 PackageManager 启用 KillYiBan 组件。
     *
     * @param appContext 应用上下文
     * @param mobile 易班手机号
     * @param password 易班密码
     * @return 操作结果 JSONObject
     */
    suspend fun initYiBan(appContext: Context, mobile: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // 易班账号必须依附于教务账号存在
                val currentUser = AppDataCenter.getCurrentUser()
                    ?: throw Exception("请先登录教务系统账号")

                // 初始化并验证易班账号
                val yiBanTask = Task(appContext)
                val result = NetworkStatus.Success.toJsonResult(yiBanTask.init(mobile, password))

                // 验证成功后，将易班凭据直接合并到当前 User 实体中
                currentUser.yibanId = mobile
                currentUser.yibanPassword = password
                AppDataCenter.saveUser(currentUser)

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