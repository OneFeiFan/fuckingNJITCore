package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent

class CurriculumsWidgetConfigActivity : BaseWidgetConfigActivity() {
    override fun getWidgetProviderClass(): Class<*> = CurriculumsWidgetProvider::class.java

    override fun updateWidgetUI(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val updateIntent = Intent(context, CurriculumsWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        context.sendBroadcast(updateIntent)
    }

    override fun onWidgetConfigured() {
        saveWidgetConfiguration()
    }
}