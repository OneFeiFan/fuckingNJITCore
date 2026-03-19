package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import com.feifan.fuckingnjit.R


// 定义抽象的小部件配置Activity
abstract class BaseWidgetConfigActivity : AppCompatActivity() {

    var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIDs =
            appWidgetManager.getAppWidgetIds(ComponentName(this, getWidgetProviderClass()))
        val appWidgetHost = AppWidgetHost(this, 0)
        widgetIDs.forEach {
            if (it != appWidgetId) {
                appWidgetManager.updateAppWidget(
                    it,
                    RemoteViews(this.packageName, R.layout.empty_widget)
                )
                appWidgetHost.deleteAppWidgetId(it)
            }
            println("widgetID: $it")
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        } else {
            onWidgetConfigured()
        }
    }

    // 保存小部件配置的方法
    protected fun saveWidgetConfiguration() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateWidgetUI(this, appWidgetManager, appWidgetId)
        finishWithResult()
    }

    // 完成配置并返回结果的方法
    private fun finishWithResult() {
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    // 抽象方法 - 获取小部件提供者类
    protected abstract fun getWidgetProviderClass(): Class<*>

    // 抽象方法 - 更新小部件UI
    protected abstract fun updateWidgetUI(
        context: android.content.Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    )

    // 钩子方法 - 是否允许多个小部件，默认不允许
    protected open fun allowMultipleWidgets(): Boolean = false

    // 钩子方法 - 获取只能创建一个小部件的提示消息
    protected open fun getSingleWidgetMessage(): String = "只能创建一个小部件"

    // 抽象方法 - 当小部件配置完成时调用
    protected abstract fun onWidgetConfigured()
}
