//package com.feifan.fuckingnjit.widget
//
//import android.appwidget.AppWidgetManager
//import android.content.Context
//
//class VerifyWifiConfiguration : BaseWidgetConfigActivity() {
//    override fun getWidgetProviderClass(): Class<*> = VerifyWifiProvider::class.java
//    override fun updateWidgetUI(
//        context: Context,
//        appWidgetManager: AppWidgetManager,
//        widgetId: Int
//    ) {
//        VerifyWifiProvider.updateWidgetUI(this, appWidgetManager, widgetId)
//    }
//
//    override fun onWidgetConfigured() {
//        saveWidgetConfiguration()
//    }
//}