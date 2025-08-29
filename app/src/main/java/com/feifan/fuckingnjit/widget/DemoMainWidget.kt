package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.utils.XiaomiUtilities

class DemoMainWidget(val context: Context) {

    fun getPermission() {
        val intent = XiaomiUtilities.getPermissionManagerIntent(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun isWidgetAlreadyCreated(): Boolean {
        val widgetIDs = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, DemoWidgetProvider::class.java))
        return widgetIDs.isNotEmpty()
    }


    fun createWidget(): String {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, DemoWidgetProvider::class.java)

        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            return """{"state":"error","message":"桌面不支持自动添加小部件，请手动添加。"}"""
        }
        // 检查是否是小米手机
        if (XiaomiUtilities.isMIUI) {
            // 检查是否已授予自定义权限
            if (!XiaomiUtilities.isCustomPermissionGranted(
                    context,
                    XiaomiUtilities.OP_INSTALL_SHORTCUT
                )
            ) {
                return """{"state":"need_permission","message":""}"""
            }
        }
        //检测是否已经创建过小部件
        if (isWidgetAlreadyCreated()) {
            return """{"state":"error","message":"只能创建一个小部件"}"""
        }
        val remoteViews = RemoteViews(context.packageName, R.layout.widget)
        val bundle = Bundle()
        bundle.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, remoteViews)

        if (!appWidgetManager.requestPinAppWidget(provider, bundle, null)) {
            return """{"state":"error","message":"小部件大概率创建失败了，可能是你的桌面不支持？？？或者没有快捷方式权限？？？"}"""
        }

        return """{"state":"success","message":"请返回桌面，自行查看小部件是否创建成功。"}"""
    }

}