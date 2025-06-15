package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.utils.XiaomiUtilities

class DemoMainWidget (val context: Context){
    fun createWidget() {
//        if (data == null||data.isEmpty()) {
//            Toast.makeText(context, "输入内容为空，无法创建组件", Toast.LENGTH_SHORT).show()
//            return
//        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, DemoWidgetProvider::class.java)

        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            Toast.makeText(context, "桌面不支持自动添加小部件，请手动添加", Toast.LENGTH_SHORT).show()
            return
        }
        // 检查是否是小米手机
        if (XiaomiUtilities.isMIUI) {
            // 检查是否已授予自定义权限
            if (!XiaomiUtilities.isCustomPermissionGranted(context, XiaomiUtilities.OP_INSTALL_SHORTCUT)) {
                // 创建并显示AlertDialog
                // 启动透明 Activity 托管对话框
                val intent = Intent(context, DialogHostActivity::class.java).apply {
                    putExtra("title", "快捷方式权限申请")
                    putExtra("message", "操作失败！！！为了固定小部件，需要授予“桌面快捷方式”权限。是否现在授予权限？")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK // 关键标志
                }
                context.startActivity(intent)
                return // 确保在用户授予权限之前不再执行后续代码
            }
        }
        val widgetIDs = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(
                context,
                DemoWidgetProvider::class.java.getName()
            ))
        //检测是否已经创建过小部件
        if (widgetIDs.isNotEmpty()) {
            Toast.makeText(context, "只能创建一个小部件", Toast.LENGTH_SHORT).show()
            return
        }
        val remoteViews = RemoteViews(context.packageName, R.layout.widget)
        val bundle = Bundle()
        bundle.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, remoteViews)

        if(!appWidgetManager.requestPinAppWidget(provider, bundle, null)){
            Toast.makeText(context,"小部件大概率创建失败了，可能是你的桌面不支持？？？或者没有快捷方式权限？？？", Toast.LENGTH_SHORT).show()
        }
    }
}