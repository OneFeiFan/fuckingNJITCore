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

/**
 * 系统级操作工具集
 *
 * 封装 Toast 提示、Loading 弹窗管理、异常处理与展示、
 * 登录流程启动、应用增量更新等通用 UI 交互逻辑。
 */
/**
 * 系统级操作工具集
 *
 * 封装 Toast 提示、Loading 弹窗管理、异常处理与展示、
 * 登录流程启动、应用增量更新等通用 UI 交互逻辑。
 */
@Suppress("unused")
object SystemActionHelper {
    /** 当前显示中的 Loading 弹窗引用，用于防止重复创建和跨方法关闭 */
    private var dialog: LoadingAnimationDialog? = null

    /**
     * 在主线程显示一条短时 Toast。
     *
     * 内部通过 [Handler] post 到主线程，因此可安全地从任意线程调用。
     *
     * @param appContext Application Context，避免 Activity 泄漏
     * @param text      提示文本内容
     */
    fun showToast(appContext: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示全局唯一的 Loading 动画弹窗。
     *
     * 若当前已有弹窗在显示，会先关闭旧弹窗再创建新的。
     * 弹窗配置：不可点击关闭、白色 20sp 文字、5 倍放大效果。
     *
     * @param text           弹窗下方展示的提示文字
     * @param activityContext Activity Context，弹窗需要绑定到窗口
     */
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

    /**
     * 关闭当前显示的 Loading 弹窗并置空引用。
     *
     * 多次调用安全，弹窗未显示或已释放时不会抛异常。
     */
    fun dismissDialog() {
        dialog?.dismiss()
        dialog = null
    }

    /**
     * 统一异常处理：打印堆栈、记录日志并向用户展示 Toast 提示。
     *
     * @param appContext Application Context，用于显示 Toast
     * @param e         捕获到的异常对象
     * @param message   向用户展示的友好提示文本
     */
    fun handleException(appContext: Context, e: Exception, message: String) {
        e.printStackTrace()
        Log.i("handleException:", message)
        showToast(appContext, message)
    }

    /**
     * 启动 WebView 登录流程。
     *
     * @param activityContext 用于 startActivity 的上下文
     * @param relogin         是否为强制重新登录，true 时跳过 logout 步骤直接复用现有状态
     * @return 正常返回空字符串；正在登录中时返回"登录中"提示
     */
    fun startLogin(activityContext: Context, relogin: Boolean = false): String {
        if (AppConfig.inLogin) {
            return "登录中"
        } else {
            AppConfig.inLogin = true
        }
        if (!relogin) {
            AppConfig.logout()
        }
        // 无论何种登录方式都清空 Cookie，防止残留会话干扰
        CookieManager.getInstance().removeAllCookies(null)
        val intent = Intent(
            activityContext,
            SampleWebViewImpl::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityContext.startActivity(intent)
        return ""
    }

    /**
     * 返回系统桌面（Home）。
     *
     * 使用 ACTION_MAIN + CATEGORY_HOME Intent，不依赖特定 Launcher。
     * 需要 FLAG_ACTIVITY_NEW_TASK 标志以确保从非 Activity 上下文也能正常跳转。
     *
     * @param appContext 应用上下文
     */
    fun goHome(appContext: Context) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(intent)
    }

    /**
     * 执行应用增量更新（bsdiff/bspatch 方案）。
     *
     * @param activityContext Activity Context，用于显示弹窗和启动安装
     * @param url             差分补丁包的下载地址
     * @return 更新是否成功，下载失败/合并失败均返回 false
     */
    suspend fun updateApp(activityContext: Context, url: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { openDialog("正在增量更新...", activityContext) }

                val pm: PackageManager = activityContext.packageManager
                val appInfo = pm.getApplicationInfo(activityContext.packageName, 0)
                // 当前已安装 APK 的绝对路径，作为 bspatch 的旧文件输入
                val oldPath = appInfo.sourceDir
                val newApkFile = File(activityContext.filesDir, "new.apk")
                val patchFile = File(activityContext.filesDir, "bin")

                // 清理上次可能残留的临时文件
                newApkFile.delete()
                patchFile.delete()
                // 下载差分补丁包
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

    /**
     * 调用系统安装器安装指定路径的 APK 文件。
     *
     * 通过 [FileProvider] 将 file:// URI 转换为 content:// URI，
     * 以适配 Android 7.0+ 的 StrictMode 限制。
     *
     * @param activityContext Activity Context，用于启动安装 Intent
     * @param apkPath         待安装 APK 的绝对路径
     */
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