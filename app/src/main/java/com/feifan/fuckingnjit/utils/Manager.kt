package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.alibaba.fastjson.JSONArray
import com.example.loadinganimation.LoadingAnimationDialog
import com.feifan.apkpatch.PatchUtils
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.service.impl.SampleWebViewImpl
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import com.feifan.fuckingnjit.widget.DemoWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class Manager {

    @SuppressLint("StaticFieldLeak")
    companion object {

        private lateinit var userManager: UserManagerImpl
        private lateinit var context: Context
        private val webService = WebServiceImpl()
        private val timeManager = TimeManager()
        private var dialog: LoadingAnimationDialog? = null
        private var inLogin = false
        private lateinit var receiver: BroadcastReceiver
        private lateinit var timetableData: List<List<List<String>>>
        private val timeMap = hashMapOf<Int, Date>()

        fun init(context: Context) {
            try {
                if (!::userManager.isInitialized) {
                    this.context = context
                    UserBoxUtils.init(context)
                    BaseDataBoxUtils.init(context)
                    userManager = UserManagerImpl(context)
                } else {
                    throw IllegalStateException("Manager already initialized")
                }
            } catch (e: Exception) {
                println("init error: ${e.message}")
            }

        }

        fun getSemesterStartDate(): String {
            val date = BaseDataBoxUtils.getSemesterStartDate()
            try {
                if (date != 0L) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    return sdf.format(Date(date))
                }
            } catch (e: Exception) {
                showToast("获取学期开始日期失败，请稍后重试")
            }
            return "2025-02-17"
        }

        fun getCurrentWeek(): Int {
            return BaseDataBoxUtils.getCurrentWeek()
        }

        fun getTimeManager(): TimeManager {
            return timeManager
        }

        fun getUserManager(): UserManagerImpl? {
            if (!::userManager.isInitialized) {
                println("userManager not initialized")
                return null
            }
            return userManager
        }

        fun getWebService(): WebServiceImpl {
            return webService
        }

        fun registerTimeReceiver() {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    println("onReceive")
                    if (getTimeManager().isInLateNightPeriod()) {
                        println("isInLateNightPeriod")
                        return
                    }
                    if (getTimetableData().isEmpty()) {
                        updateTimetableData()
                        return
                    }
                    if (getTimeMap().isEmpty()) {
                        updateTimeMap()
                        return
                    }

                    val currentTime = Date() // 获取当前时间

                    // 遍历时间映射表
                    val iterator = getTimeMap().iterator()
                    while (iterator.hasNext()) {
                        val (index, time) = iterator.next()

                        // 计算时间差（分钟）
                        val diffMinutes = (currentTime.time - time.time) / (60 * 1000)

                        when {
                            diffMinutes < -40 -> {
                                break
                            }

                            diffMinutes > 40 -> {
                                iterator.remove()
                                break // 退出循环
                            }

                            abs(diffMinutes) <= 25 -> {
                                DemoWidgetProvider.updateWidgets(context)
                                iterator.remove()
                                break // 退出循环
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_TIME_TICK)
            context.registerReceiver(receiver, filter)
            val currentWeek = BaseDataBoxUtils.getCurrentWeek()

            if (currentWeek == -1 || currentWeek > 19) {
                println("thisWeek is -1 or >19: $currentWeek")
                context.unregisterReceiver(receiver)
            }
        }

        fun unregisterTimeReceiver() {
            context.unregisterReceiver(receiver)
        }

        fun updateTimetableData() {
            val userData = UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId())
            val validTimeCourses = userData?.curriculums?.getString("validTimeCourses")
            if (validTimeCourses == null || validTimeCourses == "") {
                return
            }
            timetableData = JSONArray.parseArray(validTimeCourses) as List<List<List<String>>>
        }

        fun updateTimeMap() {
            if (timetableData.isEmpty()) {
                updateTimetableData()
                return
            }
            val dateList = timeManager.getDateList()
            val timeTable =
                timetableData[BaseDataBoxUtils.getCurrentWeek()][getTimeManager().todayWeekIndex()]
            for (i in timeTable.indices) {
                if (timeTable[i] != "") {
                    timeMap[i] = dateList[i]
                }
            }
        }

        fun getTimeMap(): HashMap<Int, Date> {
            return timeMap
        }

        fun getTimetableData(): List<List<List<String>>> {
            if (!::timetableData.isInitialized) {
                return emptyList()
            }
            return timetableData
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
                userManager.removeCurrentUser()
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
//            showToast(message)
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
    }
}
