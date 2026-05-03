package com.feifan.fuckingnjit.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import com.feifan.fuckingnjit.R


/**
 * 小部件配置 Activity 基类，用户通过长按桌面添加小部件时由系统自动启动。
 *
 * 核心职责：
 * 1. 从 Intent 提取目标 widgetId，无效时直接关闭
 * 2. 清理同一 Provider 下已存在的旧实例（用空布局覆盖后删除）
 * 3. 调用子类实现的 [onWidgetConfigured] 进入具体配置流程
 * 4. 子类完成后调用 [saveWidgetConfiguration] 写回 RESULT_OK 并关闭
 *
 * 默认行为：按返回键时 setResult(RESULT_CANCELED)，Launcher 会取消本次放置。
 */
@Suppress("unused")
abstract class BaseWidgetConfigActivity : AppCompatActivity() {

    /** 当前待配置的小部件 ID，INVALID_APPWIDGET_ID 表示无效 */
    var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 预设结果为 CANCELED，确保用户按返回键时 Launcher 自动取消本次放置
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
                // 用空布局覆盖旧实例的 UI，然后从 Host 中删除其 ID
                appWidgetManager.updateAppWidget(
                    it,
                    RemoteViews(this.packageName, R.layout.empty_widget)
                )
                appWidgetHost.deleteAppWidgetId(it)
            }
        }

        // 若 Intent 中未携带有效的 widgetId，说明调用方式异常，直接结束 Activity
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        } else {
            onWidgetConfigured()
        }
    }

    /**
     * 保存小部件配置并通知系统放置成功。
     *
     * 内部依次执行：
     * 1. 调用 [updateWidgetUI] 刷新 Widget 视图内容（由子类实现具体渲染）
     * 2. 将 RESULT_OK 与 widgetId 回传给 Launcher
     * 3. 关闭当前 Activity
     */
    protected fun saveWidgetConfiguration() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateWidgetUI(this, appWidgetManager, appWidgetId)
        finishWithResult()
    }

    /**
     * 设置 RESULT_OK 并附带 widgetId，然后 finish。
     *
     * 此方法为私有实现细节，子类不应直接调用，
     * 统一通过 [saveWidgetConfiguration] 完成整个提交流程。
     */
    private fun finishWithResult() {
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    /**
     * 返回该小部件对应的 [android.appwidget.AppWidgetProvider] 子类。
     *
     * 用于在 onCreate 中通过 ComponentName 定位已存在的旧实例以便清理。
     *
     * @return Provider 的 Class 对象
     */
    protected abstract fun getWidgetProviderClass(): Class<*>

    /**
     * 配置完成后渲染小部件的实际 UI 内容。
     *
     * 由 [saveWidgetConfiguration] 在 finish 之前调用，
     * 子类应在此方法中构造 RemoteViews 或发送广播刷新 Widget 视图。
     *
     * @param context         应用上下文
     * @param appWidgetManager 系统小部件管理器实例
     * @param widgetId        当前待配置的小部件 ID
     */
    protected abstract fun updateWidgetUI(
        context: android.content.Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    )

    /**
     * 是否允许同一 Provider 的多个小部件实例并存。
     *
     * 默认返回 false（单例模式），与 [BaseWidgetBridge.allowMultipleWidgets] 保持一致。
     * 若子类重写为 true，onCreate 中不会清理旧实例。
     *
     * @return true 表示允许多实例
     */
    protected open fun allowMultipleWidgets(): Boolean = false

    /** 当用户尝试创建第二个单例 Widget 时展示的提示文本 */
    protected open fun getSingleWidgetMessage(): String = "只能创建一个小部件"

    /**
     * onCreate 完成初始化后的配置入口，由子类实现具体业务逻辑。
     *
     * 典型流程：显示配置 UI → 用户确认 → 调用 [saveWidgetConfiguration] 提交。
     * 如果不需要额外交互，也可在此直接调用 saveWidgetConfiguration 并 finish。
     */
    protected abstract fun onWidgetConfigured()
}
