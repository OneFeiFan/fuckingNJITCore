package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.loadinganimation.LoadingAnimationDialog
import com.feifan.apkpatch.PatchUtils
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.service.impl.SampleWebViewImpl
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import com.feifan.fuckingnjit.widget.DemoWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class Manager {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var context: Context
        private var dialog: LoadingAnimationDialog? = null
        private var inLogin = false
        private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
        fun init(context: Context) {
            try {
                this.context = context
                if (!UserBoxUtils.isInitialized()) {
                    UserBoxUtils.init(context)
                }
                if (!BaseDataBoxUtils.isInitialized()) {
                    BaseDataBoxUtils.init(context)
                }

//                if(XiaomiUtilities.isFlyme) {
//                    XXPermissions.with(this.context)
//                        // 申请多个权限
//                        .permission(PermissionLists.getScheduleExactAlarmPermission())
//                        // 设置不触发错误检测机制（局部设置）
//                        //.unchecked()
//                        .request(object : OnPermissionCallback {
//
//                            override fun onResult(
//                                grantedList: MutableList<IPermission>,
//                                deniedList: MutableList<IPermission>
//                            ) {
//                                val allGranted = deniedList.isEmpty()
//                                if (!allGranted) {
//                                    // 判断请求失败的权限是否被用户勾选了不再询问的选项
////                                val doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(activity, deniedList)
//                                    // 在这里处理权限请求失败的逻辑
//                                    // ......
//                                    return
//                                }
//                                // 在这里处理权限请求成功的逻辑
//                                // ......
//                            }
//                        })
//                }
                coroutineScope.launch {
                    val week = withContext(Dispatchers.Default) {
                        val startTime = LocalDate.parse(getSemesterStartDate())
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
                        DemoWidgetProvider.updateWidgets(context)
                    }
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
                handleException(e, "获取学期开始日期失败")
            }
            return "2025-02-17"
        }

        fun getCurrentWeek(): Int {
            return BaseDataBoxUtils.getCurrentWeek()
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
