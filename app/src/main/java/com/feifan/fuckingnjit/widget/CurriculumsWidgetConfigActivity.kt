package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.feifan.fuckingnjit.widget.CurriculumsWidgetProvider.Companion.getRemoteViews

// 小部件配置Activity类
class CurriculumsWidgetConfigActivity : BaseWidgetConfigActivity() {
    override fun getWidgetProviderClass(): Class<*> = CurriculumsWidgetProvider::class.java
    override fun updateWidgetUI(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        appWidgetManager.updateAppWidget(widgetId, getRemoteViews(context, widgetId))
    }

    override fun onWidgetConfigured() {
        saveWidgetConfiguration()
    }
}