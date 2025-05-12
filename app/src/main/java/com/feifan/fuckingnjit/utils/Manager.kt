package com.feifan.fuckingnjit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import com.example.loadinganimation.LoadingAnimationDialog
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.service.impl.SampleWebViewImpl
import com.feifan.fuckingnjit.service.impl.UserManagerImpl
import com.feifan.fuckingnjit.service.impl.WebServiceImpl
import java.lang.ref.WeakReference


class Manager {

    @SuppressLint("StaticFieldLeak")
    companion object {

        private lateinit var userManager: UserManagerImpl
        private lateinit var context: Context
        private var dialogContext: WeakReference<Context> = WeakReference(null)
        private val webService = WebServiceImpl()
        private lateinit var dialog:  WeakReference<LoadingAnimationDialog>
//        private lateinit var data: SharedPreferences
//        private lateinit var semesterStartDate: String
        private var inLogin = false

        @SuppressLint("UseCompatLoadingForDrawables")
        fun init(context: Context) {
            if (!this::context.isInitialized) {
                this.context = context
                userManager = UserManagerImpl(context)
//                data = context.getSharedPreferences("data", Context.MODE_PRIVATE)
//                if(!data.contains("SEMESTER_START_DATE")){
//                    CoroutineScope(Dispatchers.Main).launch {
//                        try {
//                            val temp = webService.getSemesterStartDate()
//                            if(!temp.contains("error")){
//                                semesterStartDate = temp
//                                data.edit() { putString("SEMESTER_START_DATE", semesterStartDate) }
//                            }else{
//                                semesterStartDate = ""
//                                showToast("获取学期开始日期失败，请稍后重试")
//                            }
//                            // 处理 startDate，例如保存到 SharedPreferences
//
//                        } catch (e: Exception) {
//                            // 处理异常，例如显示错误信息
//                            showToast("获取学期开始日期失败，请稍后重试")
//                        }
//                    }
//                }else{
//                    semesterStartDate = data.getString("SEMESTER_START_DATE", "") as String
//                }
            } else {
                throw IllegalStateException("Manager already initialized")
            }
            return
        }
        fun showToast(text: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
        fun getSemesterStartDate(): String {
            return userManager.getCurrentUser().getSemesterStartDate()
        }
        fun getUserManager(): UserManagerImpl {
            return userManager
        }
        fun startLogin(relogin: Boolean = false): String {
            if (!this::context.isInitialized) {
                return "请先初始化Manager对象"
            }
            if (inLogin) {
                return "已登录"
            }else {
                inLogin = true
            }
            if (!relogin) {
                logout()
            }
            val intent = Intent(context, SampleWebViewImpl::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return ""
        }
        fun endLogin() {
            inLogin = false
            getSemesterStartDate()
        }

        fun logout(switch: Boolean = true) {
            if (switch) {
                userManager.removeCurrentUser()
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
        }

        fun getWebService(): WebServiceImpl {
            return webService
        }

        fun openDialog(text: String, context_: Context) {
//            if (context_ !is Activity) {
//                throw IllegalArgumentException("Context must be an instance of Activity")
//            }
            if (dialogContext.get() == null) {
                dialogContext = WeakReference(context_)
                dialog = WeakReference<LoadingAnimationDialog>(LoadingAnimationDialog(context_))
//                AppWatcher.objectWatcher.expectWeaklyReachable(dialog,"加载框")
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
                }else {
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
    }
}
