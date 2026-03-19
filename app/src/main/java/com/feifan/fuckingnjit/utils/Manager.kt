package com.feifan.fuckingnjit.utils

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
import com.alibaba.fastjson.JSONObject
import com.example.loadinganimation.LoadingAnimationDialog
import com.feifan.apkpatch.PatchUtils
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.model.YiBan
import com.feifan.fuckingnjit.service.impl.SampleWebViewImpl
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.DbClearHelper
import com.feifan.fuckingnjit.utils.database.UserBoxUtils
import com.feifan.fuckingnjit.utils.database.YiBanBoxUtils
import com.feifan.fuckingnjit.widget.CurriculumsWidgetProvider
import com.feifan.yiban.Apis.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

//        fun getSemesterStartDate(): String {
//            return TimeManager.getInstance().getSemesterStartDate()
//        }

//        fun getCurrentWeek(): Int {
//            return BaseDataBoxUtils.getCurrentWeek()
//        }

        fun getPermissionsManager(): PermissionsManager {
            return PermissionsManager.getInstance(context)
        }

//        fun setLocalCurriculums(data: String) {
//            println(data)
//            val json = JSONObject.parseObject(data)
//            val userId = BaseDataBoxUtils.getCurrentUserId()
//            val user = UserBoxUtils.getUserById(userId)
//            var localCurriculums = user?.localCurriculums
//
//            if (localCurriculums == null) {
//                localCurriculums = JSONObject()
//            }
//
//
//
//                // 遍历 localCurriculums 的所有键
//                val keys = json.keys.iterator()
//                while (keys.hasNext()) {
//                    val key = keys.next()
//                    localCurriculums[key] = json[key] // 获取值
//                }
//                user?.localCurriculums = localCurriculums
//            user?.let { UserBoxUtils.updateUser(it) }
//
//        }

//        fun modifyLocalCurriculums(keys: String) {
//            println(keys)
//            val array = JSONArray.parseArray(keys)
//            val userId = BaseDataBoxUtils.getCurrentUserId()
//            val user = UserBoxUtils.getUserById(userId)
//            var localCurriculums = user?.localCurriculums
//
//            if (localCurriculums == null) {
//                localCurriculums = JSONObject()
//            }
//
//                println(localCurriculums.toJSONString())
//                // 遍历 localCurriculums 的所有键
//                for (key in array) {
//                    if(localCurriculums.containsKey(key)){
//                        localCurriculums.remove(key)
//                    }
//                }
//                user?.localCurriculums = localCurriculums
//            user?.let { UserBoxUtils.updateUser(it) }
//
//        }

//        fun reSetLocalCurriculums() {
//            val userId = BaseDataBoxUtils.getCurrentUserId()
//            val user = UserBoxUtils.getUserById(userId)
//            user?.localCurriculums = JSONObject()
//            user?.let { UserBoxUtils.updateUser(it) }
//        }

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
    }
}
