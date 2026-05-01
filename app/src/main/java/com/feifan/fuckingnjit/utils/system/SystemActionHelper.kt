package com.feifan.fuckingnjit.utils.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.loadinganimation.LoadingAnimationDialog
import com.feifan.apkpatch.PatchUtils
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.service.impl.SampleWebViewImpl
import com.feifan.fuckingnjit.utils.AppConfig
import com.feifan.fuckingnjit.utils.network.HttpRequestHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SystemActionHelper {
    private var dialog: LoadingAnimationDialog? = null

    fun showToast(appContext: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun openDialog(text: String, activityContext: Context) {
        if (dialog != null && dialog!!.isShowing) {
            dialog!!.dismiss()
        }
        dialog = LoadingAnimationDialog(activityContext)
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

    fun handleException(appContext: Context, e: Exception, message: String) {
        e.printStackTrace()
        Log.i("handleException:", message)
        showToast(appContext, message)
    }

    fun startLogin(activityContext: Context, relogin: Boolean = false): String {
        if (AppConfig.inLogin) {
            return "已登录"
        } else {
            AppConfig.inLogin = true
        }
        if (!relogin) {
            AppConfig.logout()
        }
        CookieManager.getInstance().removeAllCookies(null)
        val intent = Intent(
            activityContext,
            SampleWebViewImpl::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityContext.startActivity(intent)
        return ""
    }

    fun goHome(appContext: Context) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(intent)
    }

    suspend fun updateApp(activityContext: Context, url: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { openDialog("正在增量更新...", activityContext) }

                val pm: PackageManager = activityContext.packageManager
                val appInfo = pm.getApplicationInfo(activityContext.packageName, 0)
                val oldPath = appInfo.sourceDir
                val newApkFile = File(activityContext.filesDir, "new.apk")
                val patchFile = File(activityContext.filesDir, "bin")

                newApkFile.delete()
                patchFile.delete()
                // 异步下载文件
                HttpRequestHelper.downloadFile(url, "bin", activityContext)

                if (!patchFile.exists()) {
                    withContext(Dispatchers.Main) { showToast(activityContext, "下载增量包失败") }
                    return@withContext false
                }

                val result =
                    PatchUtils.patch(oldPath, newApkFile.absolutePath, patchFile.absolutePath)

                if (result == 0) {
                    withContext(Dispatchers.Main) {
                        install(
                            activityContext,
                            newApkFile.absolutePath
                        )
                    }
                    true
                } else {
                    withContext(Dispatchers.Main) { showToast(activityContext, "合并失败") }
                    false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { handleException(activityContext, e, "操作失败") }
                false
            } finally {
                withContext(Dispatchers.Main) { dismissDialog() }
            }
        }

    private fun install(activityContext: Context, apkPath: String) {
        val file = File(apkPath)
        val uri = FileProvider.getUriForFile(
            activityContext,
            activityContext.packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activityContext.startActivity(intent)
    }
}