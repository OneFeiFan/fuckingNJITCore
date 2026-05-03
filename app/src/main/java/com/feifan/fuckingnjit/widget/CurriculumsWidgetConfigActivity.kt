package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent

/**
 * 课表小部件配置 Activity，用户在桌面添加课表 Widget 时自动拉起。
 *
 * 继承 [BaseWidgetConfigActivity] 的通用配置流程：
 * 1. 用户确认后保存配置信息
 * 2. 通过广播触发 [CurriculumsWidgetProvider] 立即刷新一次 UI
 */
class CurriculumsWidgetConfigActivity : BaseWidgetConfigActivity() {
    override fun getWidgetProviderClass(): Class<*> = CurriculumsWidgetProvider::class.java

    /**
     * 配置完成后立即通过广播刷新对应 widgetId 的课表 UI，
     * 避免用户等待下一次心跳更新周期。
     */
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

    /** 用户确认配置后的回调，委托给父类保存小部件配置信息 */
    override fun onWidgetConfigured() {
        saveWidgetConfiguration()
    }
}