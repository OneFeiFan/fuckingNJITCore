package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.Context

// 小部件配置Activity类
class DemoWidgetConfigurationActivity : BaseWidgetConfigActivity() {
    override fun getWidgetProviderClass(): Class<*> = CurriculumsWidgetProvider::class.java
    override fun updateWidgetUI(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        CurriculumsWidgetProvider.updateWidgetUI(this, appWidgetManager, widgetId)
    }

    override fun onWidgetConfigured() {
        saveWidgetConfiguration()
    }
}