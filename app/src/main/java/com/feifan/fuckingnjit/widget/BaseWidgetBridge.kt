package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.feifan.fuckingnjit.utils.system.XiaomiUtilities

/**
 * 桌面小部件基础桥接类
 *
 * 封装了小部件的创建、权限检查和 MIUI 兼容处理等通用逻辑。
 * 子类只需实现抽象方法指定具体的 Provider 和布局资源即可。
 *
 * @param context 应用上下文
 */
@Suppress("unused")
abstract class BaseWidgetBridge(protected val context: Context) {

    /**
     * 跳转到 MIUI 权限管理界面获取自定义权限
     */
    open fun getPermission() {
        val intent = XiaomiUtilities.getPermissionManagerIntent(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 检查桌面是否已创建本Provider对应的小部件实例
     *
     * @return true 表示已存在至少一个实例
     */
    open fun isWidgetAlreadyCreated(): Boolean {
        val widgetIDs = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, getWidgetProviderClass()))
        return widgetIDs.isNotEmpty()
    }

    /**
     * 请求系统在桌面创建小部件（Pin 模式）
     *
     * 自动处理 MIUI 权限检测、重复创建防护以及桌面兼容性校验。
     *
     * @return JSON 格式的状态结果，包含 state 和 message 字段
     */
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

    /**
     * 返回该小部件对应的 [android.appwidget.AppWidgetProvider] 子类。
     *
     * 用于在 [isWidgetAlreadyCreated] 和 [createWidget] 中通过 ComponentName 定位 Provider。
     *
     * @return Provider 的 Class 对象
     */
    protected abstract fun getWidgetProviderClass(): Class<*>

    /**
     * 返回小部件的布局资源 ID，用于创建 Pin 模式下的预览 RemoteViews。
     *
     * @return layout 资源 ID（如 R.layout.xxx_widget）
     */
    protected abstract fun getWidgetLayoutResId(): Int

    /**
     * 是否允许同一 Provider 在桌面上创建多个实例。
     *
     * 默认返回 false（单例模式），多数课表/日程类 Widget 只需要一个。
     * 如需支持多实例（如不同尺寸各一个），子类可重写返回 true。
     *
     * @return true 表示允许多个实例并存
     */
    protected open fun allowMultipleWidgets(): Boolean = false

    /** 当系统 Launcher 不支持 requestPinAppWidget API 时向用户展示的提示文本 */
    protected open fun getUnsupportedMessage(): String = "桌面不支持自动添加小部件，请手动添加。"

    /** 当用户尝试创建第二个实例但当前 Widget 不允许多实例时展示的提示文本 */
    protected open fun getSingleWidgetMessage(): String = "只能创建一个小部件"

    /**
     * 调用 [AppWidgetManager.requestPinAppWidget] 返回 false 时展示的提示文本。
     *
     * 通常意味着桌面 Launcher 不支持 Pin 协议或权限被拒绝。
     */
    protected open fun getCreationFailedMessage(): String =
        "小部件大概率创建失败了，可能是你的桌面不支持？？？或者没有快捷方式权限？？？"

    /**
     * requestPinAppWidget 调用成功后展示的提示文本。
     *
     * 注意：API 返回成功不代表桌面一定显示了 Widget，仅表示请求已发出，
     * 最终效果取决于 Launcher 实现。
     */
    protected open fun getSuccessMessage(): String = "请返回桌面，自行查看小部件是否创建成功。"
}
