package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import com.alibaba.fastjson.JSONArray
import com.example.loadinganimation.LoadingAnimationDialog
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.service.impl.SampleWebViewImpl
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import com.feifan.fuckingnjit.widget.DemoWidgetProvider
import java.lang.ref.WeakReference
import java.util.Date
import kotlin.math.abs

class Manager {

    @SuppressLint("StaticFieldLeak")
    companion object {

        private lateinit var userManager: UserManagerImpl
        private lateinit var context: Context
        private var dialogContext: WeakReference<Context> = WeakReference(null)
        private val webService = WebServiceImpl()
        private val timeManager = TimeManager()
        private lateinit var dialog: WeakReference<LoadingAnimationDialog>
        private var inLogin = false
        private lateinit var receiver: BroadcastReceiver
        private lateinit var timetableData: List<List<List<String>>>
        private var thisWeek: Int = -1
        private val timeMap = mutableMapOf<Int, Date>()

        fun init(context: Context) {
            if (!::userManager.isInitialized) {
                this.context = context
                userManager = UserManagerImpl(context)
            } else {
                throw IllegalStateException("Manager already initialized")
            }
        }

        fun getSemesterStartDate(): String {
            val date = getUserManager()?.getCurrentUser()?.getSemesterStartDate()
            try {
                if (date != null && date != "") {
                    thisWeek = timeManager.calculateCurrentWeek(date)
                }
            } catch (e: Exception) {
                showToast("获取学期开始日期失败，请稍后重试")
            }
            return date?: ""
        }

        fun getThisWeek(): Int {
            return thisWeek
        }

        fun getTimeManager(): TimeManager {
            return timeManager
        }

        fun getUserManager(): UserManagerImpl? {
            if (!::userManager.isInitialized) {
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
                    if (timeManager.isInLateNightPeriod()) {
                        return
                    }
                    if (timetableData.isEmpty()) {
                        updateTimetableData()
                        return
                    }
                    if (timeMap.isEmpty()) {
                        updateTimeMap()
                        return
                    }

                    val currentTime = Date() // 获取当前时间

                    // 遍历时间映射表
                    val iterator = timeMap.iterator()
                    while (iterator.hasNext()) {
                        val (index, time) = iterator.next()

                        // 计算时间差（分钟）
                        val diffMinutes = (currentTime.time - time.time) / (60 * 1000)

                        when {
                            diffMinutes < -30 -> break
                            diffMinutes > 30 -> {
                                iterator.remove()
                            }
                            abs(diffMinutes) <= 10 -> {
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

            if (thisWeek == -1 || thisWeek > 19) {
                println("thisWeek is -1 or >19: $thisWeek")
                context.unregisterReceiver(receiver)
            }
        }

        fun unregisterTimeReceiver() {
            context.unregisterReceiver(receiver)
        }

        fun updateTimetableData() {
            val curriculums = getUserManager()?.getCurrentUser()?.getCurriculums()
            if (curriculums == null || curriculums == "") {
                return
            }
            timetableData = JSONArray.parseArray(curriculums) as List<List<List<String>>>
            println("updateTimetableData")
            println(timetableData)
        }

        fun updateTimeMap() {
            if (timetableData.isEmpty()) {
                updateTimetableData()
                return
            }
            val dateList = timeManager.getDateList()
            val timeTable = timetableData[thisWeek][getTimeManager().todayWeekIndex()]

            for (i in timeTable.indices) {
                if (timeTable[i] != "") {
                    timeMap[i] = dateList[i]
                }
            }
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
            val intent = Intent(context, SampleWebViewImpl::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return ""
        }

        fun endLogin() {
            inLogin = false
            getSemesterStartDate()
        }

        fun logout(removeCurrentUser: Boolean = true) {
            if (removeCurrentUser) {
                userManager.removeCurrentUser()
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
        }

        fun showToast(text: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }

        fun openDialog(text: String, context_: Context) {
            // if (context_ !is Activity) {
            //     throw IllegalArgumentException("Context must be an instance of Activity")
            // }
            if (dialogContext.get() == null) {
                dialogContext = WeakReference(context_)
                dialog = WeakReference(LoadingAnimationDialog(context_))
                // AppWatcher.objectWatcher.expectWeaklyReachable(dialog,"加载框")
            }
            dialog.get()?.apply {
                setCloseOnClick(false)
                setProgressVector(R.drawable.loading)
                setTextViewVisibility(true)
                setTextStyle(true)
                setTextColor(Color.WHITE)
                setTextSize(20F)
                setEnlarge(5)
                setTextMsg(text)
                if (!isShowing) {
                    show()
                } else {
                    closeDialog()
                    show()
                }
            }
        }

        fun closeDialog() {
            dialog.get()?.hide()
        }

        fun dismissDialog() {
            dialog.get()?.dismiss()
            dialog.clear()
        }

        fun handleException(e: Exception, message: String) {
            e.printStackTrace()
            println("handleException: $message")
            showToast(message)
        }
    }
}
