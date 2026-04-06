package com.feifan.fuckingnjit.utils

import SleepUploadPayload
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.example.loadinganimation.LoadingAnimationDialog
import com.feifan.apkpatch.PatchUtils
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.model.SleepRecord
import com.feifan.fuckingnjit.model.YiBan
import com.feifan.fuckingnjit.service.impl.SampleWebViewImpl
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.DbClearHelper
import com.feifan.fuckingnjit.utils.database.SleepRecordBoxUtils
import com.feifan.fuckingnjit.utils.database.SleepSensorBoxUtils
import com.feifan.fuckingnjit.utils.database.UserBoxUtils
import com.feifan.fuckingnjit.utils.database.YiBanBoxUtils
import com.feifan.fuckingnjit.widget.CurriculumsWidgetProvider
import com.feifan.yiban.Apis.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import java.io.File
import java.time.LocalDate
import java.time.ZoneId


class Manager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var context: Context
        private var dialog: LoadingAnimationDialog? = null
        private var inLogin = false
        private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
        private val YiBanTask: Task by lazy { Task(context) }

        fun init(context: Context) {
            try {
                this.context = context
                if (!UserBoxUtils.isInitialized()) {
                    UserBoxUtils.init(context)
                    DbClearHelper.checkAndClear(context, UserBoxUtils.getBoxStore()!!, "user_1.2.5")
                }
                if (!BaseDataBoxUtils.isInitialized()) {
                    BaseDataBoxUtils.init(context)
                    DbClearHelper.checkAndClear(
                        context,
                        BaseDataBoxUtils.getBoxStore()!!,
                        "base_1.2.5"
                    )
                }
                if(!SleepSensorBoxUtils.isInitialized()){
                    SleepSensorBoxUtils.init(context)
                }
                coroutineScope.launch {
                    val week = withContext(Dispatchers.Default) {
                        val startTime =
                            LocalDate.parse(TimeManager.getInstance().getSemesterStartDate())
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        TimeManager.getInstance().calculateCurrentWeek(startTime)
                    }

                    // 阶段2：IO操作 → IO
                    withContext(Dispatchers.IO) {
                        BaseDataBoxUtils.updateBaseData { it.currentWeek = week }
                    }

                    // 阶段3：UI更新 → Main
                    withContext(Dispatchers.Main) {
                        CurriculumsWidgetProvider.updateWidgets(context)
//                        WifiUtils.initialize(context)
                    }
                }
            } catch (e: Exception) {
                println("init error: ${e.message}")
            }

        }

        suspend fun initYiBan(mobile: String, password: String): JSONObject =
            withContext(Dispatchers.IO) {
                return@withContext try {
                    println("initYiBan: $mobile, $password")
                    if (!YiBanBoxUtils.isInitialized()) {
                        YiBanBoxUtils.init(context)
                    }
                    var user =
                        YiBanBoxUtils.getUserById(mobile) ?: YiBan(id = mobile, password = password)
                    println("user: $user")
                    val result =
                        NetworkStatus.Success.toJsonResult(YiBanTask.init(user.id, user.password))
                    println("result: $result")
                    YiBanBoxUtils.insertUser(user)
                    user = YiBanBoxUtils.getUserById(mobile)!!
                    BaseDataBoxUtils.updateBaseData { it.currentYiBanId = user.uuid }
                    val packageManager = context.packageManager
                    val componentName = ComponentName(context, KillYiBan::class.java)

                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    result
                } catch (e: Exception) {
                    handleException(e, "初始化易班失败")
                    NetworkStatus.UnknownError.toJsonResult(e.message)
                }
            }

        fun getPermissionsManager(): PermissionsManager {
            return PermissionsManager.getInstance(context)
        }

        fun getTimeManager(): TimeManager {
            return TimeManager.getInstance()
        }

        fun getUserManager(): UserManagerImpl {
            return UserManagerImpl.getInstance()
        }

        fun getWebService(): WebServiceImpl {
            return WebServiceImpl.getInstance()
        }

        fun startLogin(relogin: Boolean = false): String {
            if (!this::context.isInitialized) {
                return "请先初始化Manager对象"
            }
            if (inLogin) {
                return "已登录"
            } else {
                inLogin = true
            }
            if (!relogin) {
                logout()
            }
            CookieManager.getInstance().removeAllCookies(null)
            val intent = Intent(
                context,
                SampleWebViewImpl::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return ""
        }

        fun endLogin() {
            inLogin = false
        }

        fun logout(removeCurrentUser: Boolean = true) {
            if (removeCurrentUser) {
                UserManagerImpl.getInstance().removeCurrentUser()
            }
        }

        fun showToast(text: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }

        fun openDialog(text: String, context_: Context) {
            if (dialog != null && dialog!!.isShowing) {
                dialog!!.dismiss()
            }
            dialog = LoadingAnimationDialog(context_)

            dialog?.apply {
                setCloseOnClick(false)
                setProgressVector(R.drawable.loading)
                setTextViewVisibility(true)
                setTextStyle(true)
                setTextColor(Color.WHITE)
                setTextSize(20F)
                setEnlarge(5)
                setTextMsg(text)
                show()
            }
        }

        fun dismissDialog() {
            dialog?.dismiss()
            dialog = null
        }

        fun handleException(e: Exception, message: String) {
            e.printStackTrace()
            println("handleException: $message")
            showToast(message)
        }

        fun goHome() {
            if (!this::context.isInitialized) {
                return
            }
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        fun setWifiAuthTupe(type: String) {
            BaseDataBoxUtils.updateBaseData { it.wifiAuthTupe = type }
        }

        fun getWifiAuthTupe(): String {
            return BaseDataBoxUtils.getWifiAuthTupe()
        }

        suspend fun updateApp(url: String): Boolean = withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    openDialog("正在增量更新...", context)
                }
                val pm: PackageManager = context.packageManager
                val appInfo = pm.getApplicationInfo(context.packageName, 0)
                val oldPath = appInfo.sourceDir
                val newApkFile = File(context.filesDir, "new.apk")
                val patchFile = File(context.filesDir, "bin")

                newApkFile.delete()
                patchFile.delete()
                // 异步下载文件
                HttpRequestHelper.downloadFile(url, "bin", context)

                if (!patchFile.exists()) {
                    withContext(Dispatchers.Main) {
                        showToast("下载增量包失败")
                    }
                    return@withContext false
                }

                // 在IO线程执行耗时操作
                val result =
                    PatchUtils.patch(oldPath, newApkFile.absolutePath, patchFile.absolutePath)

                if (result == 0) {
                    withContext(Dispatchers.Main) {
                        install(newApkFile.absolutePath)
                    }
                    true
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("合并失败")
                    }
                    false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleException(e, "操作失败")
                }
                false
            } finally {
                withContext(Dispatchers.Main) {
                    dismissDialog()
                }
            }
        }

        private fun install(apkPath: String) {
            val file = File(apkPath)
            val uri =
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider", file
                )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 添加临时读取权限
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(intent)
        }

        suspend fun uploadAndClearData() = withContext(Dispatchers.IO) {
            try {
                val userId = getUserManager().getCurrentUser().id

                val deviceModel = android.os.Build.MODEL.replace(" ", "_")
                // ==========================================
                // 第一步：先斩后奏！上传前清空过期数据
                // ==========================================
                val splitTimeMs = getTimeManager().getLatestSessionSplitTimeMs()
                // 复用了你写好的极速清理方法，干掉 12:00 前的历史数据
                SleepSensorBoxUtils.deleteRecordsBefore(splitTimeMs)

                // ==========================================
                // 第二步：获取剩下的、绝对新鲜的活跃数据
                // ==========================================
                val uploadRecords = SleepSensorBoxUtils.getAll() // 获取全部剩下的
                if (uploadRecords.isEmpty()) return@withContext

                // 转换结构
                val uploadPoints = uploadRecords.map { UploadSensorPoint.fromLocalRecord(it) }
                val payload = SleepUploadPayload(userId = userId+deviceModel, data = uploadPoints)

                // 发送请求
                val requestBody = JSONObject.toJSONString(payload)
                val customHeaders = Headers.Builder().apply {
                    // [核心新增] 添加 ngrok 专属头
                    add("ngrok-skip-browser-warning", "true")
                }.build()
                val responseString = HttpRequestHelper.executeBaseRequest(
                    url = "https://unplacid-davian-unatoned.ngrok-free.dev/api/sleep/upload",
                    method = HttpMethod.POST,
                    jsonStr = requestBody,
                    headers = customHeaders
                    // headers 和 cookie 这里睡眠服务器不需要，所以不用传，用默认的即可
                )

                val responseObj = JSON.parseObject(responseString)

                if (responseObj != null && responseObj.getInteger("code") == 200) {
                    val dataObj = responseObj.getJSONObject("data")

                    if (dataObj != null && dataObj.isNotEmpty()) {
                        // 遍历返回的每一天数据 (解决可能存在的断网多天补发情况)
                        val targetDate = LocalDate.now().toString()

                            val sleepHours = dataObj.getDoubleValue("sleepHours")
                            val startTimeMs = dataObj.getLongValue("sleepStartTimeMs")
                            val wakeUpTimeMs = dataObj.getLongValue("wakeUpTimeMs")

                            if (sleepHours > 0) {
                                val totalMinutes = (sleepHours * 60).toInt()

                                // 从 ObjectBox 查询今天是否已经有记录了
                                // 假设你在 SleepRecordBoxUtils 中写了 getByDate(date: String) 方法
                                val existingRecord = SleepRecordBoxUtils.getByDate(targetDate)

                                if (existingRecord != null) {
                                    // 更新已有记录
                                    existingRecord.totalSleepMinutes = totalMinutes
                                    existingRecord.sleepStartTimeMs = startTimeMs
                                    existingRecord.wakeUpTimeMs = wakeUpTimeMs
                                    SleepRecordBoxUtils.insertOrUpdate(existingRecord)
                                } else {
                                    // 创建全新记录
                                    val newRecord = SleepRecord(
                                        targetDate = targetDate,
                                        totalSleepMinutes = totalMinutes,
                                        sleepStartTimeMs = startTimeMs,
                                        wakeUpTimeMs = wakeUpTimeMs
                                    )
                                    SleepRecordBoxUtils.insertOrUpdate(newRecord)
                                }

                                println("🎉 [$targetDate] 的睡眠数据已完美落盘: 睡了 $totalMinutes 分钟，起止时间均已保存！")
                            }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("睡眠数据上传异常: ${e.message}")
            }
        }

    }
}
