package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.feifan.fuckingnjit.utils.XiaomiUtilities

abstract class BaseWidgetBridge(protected val context: Context) {

    // 获取权限
    open fun getPermission() {
        val intent = XiaomiUtilities.getPermissionManagerIntent(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // 检查小部件是否已创建
    open fun isWidgetAlreadyCreated(): Boolean {
        val widgetIDs = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, getWidgetProviderClass()))
        return widgetIDs.isNotEmpty()
    }

    // 创建小部件
    fun createWidget(): String {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, getWidgetProviderClass())

        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            return """{"state":"error","message":"${getUnsupportedMessage()}"}"""
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

        // 检测是否已经创建过小部件
        if (!allowMultipleWidgets() && isWidgetAlreadyCreated()) {
            return """{"state":"error","message":"${getSingleWidgetMessage()}"}"""
        }

        val remoteViews = RemoteViews(context.packageName, getWidgetLayoutResId())
        val bundle = Bundle()
        bundle.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, remoteViews)

        if (!appWidgetManager.requestPinAppWidget(provider, bundle, null)) {
            return """{"state":"error","message":"${getCreationFailedMessage()}"}"""
        }

        return """{"state":"success","message":"${getSuccessMessage()}"}"""
    }

    // 抽象方法 - 获取小部件提供者类
    protected abstract fun getWidgetProviderClass(): Class<*>

    // 抽象方法 - 获取小部件布局资源ID
    protected abstract fun getWidgetLayoutResId(): Int

    // 钩子方法 - 是否允许多个小部件，默认不允许
    protected open fun allowMultipleWidgets(): Boolean = false

    // 钩子方法 - 获取不支持的消息
    protected open fun getUnsupportedMessage(): String = "桌面不支持自动添加小部件，请手动添加。"

    // 钩子方法 - 获取只能创建一个小部件的提示消息
    protected open fun getSingleWidgetMessage(): String = "只能创建一个小部件"

    // 钩子方法 - 获取创建失败的消息
    protected open fun getCreationFailedMessage(): String =
        "小部件大概率创建失败了，可能是你的桌面不支持？？？或者没有快捷方式权限？？？"

    // 钩子方法 - 获取创建成功的消息
    protected open fun getSuccessMessage(): String = "请返回桌面，自行查看小部件是否创建成功。"
}

//// 具体实现类
//class DemoMainWidget(context: Context) : AbstractMainWidget(context) {
//    override fun getWidgetProviderClass(): Class<*> = DemoWidgetProvider::class.java
//
//    override fun getWidgetLayoutResId(): Int = R.layout.widget
//
//    // 可以覆盖其他方法来自定义行为
//    // override fun allowMultipleWidgets(): Boolean = true
//    // override fun getSingleWidgetMessage(): String = "自定义提示消息"
//}
