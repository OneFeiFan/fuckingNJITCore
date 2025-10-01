package com.feifan.fuckingnjit.widget

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.utils.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.UserBoxUtils
import com.hjq.window.EasyWindow
import com.hjq.window.EasyWindowManager
import com.hjq.window.OnWindowViewClickListener
import com.hjq.window.OnWindowViewLongClickListener
import com.hjq.window.draggable.SpringBackWindowDraggableRule
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class VerifyWifiProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_VERIFY_WIFI = "VERIFY_WIFI"
        private const val EXTRA_WIDGET_ID = "WIDGET_ID"
        private const val MAX_RETRY_COUNT = 2
        private const val ANIMATION_DURATION = 800L
        private const val WINDOW_DISMISS_DELAY = 3000L

        @Volatile
        private var httpClient: OkHttpClient? = null

        private val httpClientInstance: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }

        fun getRemoteViews(context: Context, widgetId: Int): RemoteViews {
            return RemoteViews(context.packageName, R.layout.wifiwidget).apply {
                val intent = Intent(context, VerifyWifiProvider::class.java).apply {
                    action = ACTION_VERIFY_WIFI
                    putExtra(EXTRA_WIDGET_ID, widgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    widgetId, // 使用widgetId作为请求码确保唯一性
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                setOnClickPendingIntent(R.id.wifi_widget, pendingIntent)
            }
        }

        private fun getHttpClient(): OkHttpClient {
            return httpClient ?: synchronized(this) {
                httpClient ?: httpClientInstance.also { httpClient = it }
            }
        }

        fun updateWidgetUI(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            appWidgetManager.updateAppWidget(widgetId, getRemoteViews(context, widgetId))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_VERIFY_WIFI -> handleVerifyWifiAction(context, intent)
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> updateAllWidgets(context)
        }
    }

    private fun handleVerifyWifiAction(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (!UserBoxUtils.isInitialized()) {
            println("UserBoxUtils not initialized")
            UserBoxUtils.init(context)
        }
        if (!BaseDataBoxUtils.isInitialized()) {
            println("BaseDataBoxUtils not initialized")
            BaseDataBoxUtils.init(context)
        }
        openWifiSettings(context)
        showFloatingWindow(context.applicationContext as Application)
    }

    private fun openWifiSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun showFloatingWindow(application: Application) {
        EasyWindowManager.cancelAllWindow()
        EasyWindow.with(application)
            .setContentView(R.layout.floating_button)
            .setOutsideTouchable(true)
            .setWindowLocationPercent(Gravity.END or Gravity.BOTTOM, 0f, 0.4f)
            .setWindowDraggableRule(SpringBackWindowDraggableRule())
            .setOnClickListenerByView(R.id.floating_button, FloatingButtonClickListener())
            .setOnLongClickListenerByView(R.id.floating_button,
                object : OnWindowViewLongClickListener<FrameLayout> {
                    override fun onLongClick(
                        easyWindow: EasyWindow<*>,
                        view: FrameLayout
                    ): Boolean {
                        val button = view.findViewById<View>(R.id.floating_button)
                        view.postDelayed({
                            EasyWindowManager.cancelAllWindow()
                            (button.background as? GradientDrawable)?.setColor(Color.WHITE)
                        }, WINDOW_DISMISS_DELAY)
                        return true
                    }
                })
            .show()
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, this::class.java)
        )
        widgetIds.forEach { updateWidgetUI(context, appWidgetManager, it) }
    }


    private class FloatingButtonClickListener : OnWindowViewClickListener<FrameLayout> {
        private var animator: ValueAnimator? = null
        private var isAnimating = false
        private var currentCall: Call? = null

        override fun onClick(easyWindow: EasyWindow<*>, view: FrameLayout) {
            val button = view.findViewById<View>(R.id.floating_button)
            stopBreathingAnimation()
            startBreathingAnimation(button)
            executeNetworkRequest(WeakReference(view), WeakReference(button))
        }

        private fun startBreathingAnimation(button: View) {
            val background = (button.background as? GradientDrawable) ?: run {
                GradientDrawable().apply {
                    cornerRadius = 100f
                    button.background = this
                }
            }

            animator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                duration = ANIMATION_DURATION
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()

                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    val color = ArgbEvaluator().evaluate(
                        value,
                        Color.WHITE,
                        Color.GREEN
                    ) as Int
                    background.setColor(color)
                }
                start()
            }
            isAnimating = true
        }

        private fun stopBreathingAnimation() {
            animator?.let {
                it.removeAllUpdateListeners()
                it.cancel()
            }
            animator = null
            isAnimating = false
        }

        private fun executeNetworkRequest(
            viewRef: WeakReference<FrameLayout>,
            buttonRef: WeakReference<View>
        ) {
            val userId = BaseDataBoxUtils.getCurrentUserId()
            val type = BaseDataBoxUtils.getWifiAuthTupe()
            val user = UserBoxUtils.getUserById(userId)

            var retryCount = 0
            val url =
                "http://172.31.255.156:801/eportal/portal/login?login_method=1&user_account=${user?.id + type}&user_password=${user?.password}"

            fun handleResult(success: Boolean) {
                viewRef.get()?.post {
                    stopBreathingAnimation()
                    buttonRef.get()?.background?.let { bg ->
                        (bg as? GradientDrawable)?.setColor(if (success) Color.GREEN else Color.RED)
                    }
                    viewRef.get()?.postDelayed({
                        buttonRef.get()?.background?.let { bg ->
                            (bg as? GradientDrawable)?.setColor(Color.WHITE)
                        }
                        EasyWindowManager.cancelAllWindow()
                    }, WINDOW_DISMISS_DELAY)
                }
            }

            fun performRequest() {
                currentCall = getHttpClient().newCall(Request.Builder().url(url).build()).apply {
                    enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (retryCount++ < MAX_RETRY_COUNT) {
                                performRequest()
                            } else {
                                handleResult(false)
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                val body = it.body?.string() ?: ""
                                handleResult(body.contains("认证成功") || body.contains("AC999"))
                            }
                        }
                    })
                }
            }

            currentCall?.cancel()
            performRequest()
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        // 清理资源
        httpClient = null
    }
}