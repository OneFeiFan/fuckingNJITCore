package com.feifan.fuckingnjit.widget

import android.content.Context
import com.feifan.fuckingnjit.R

/**
 * 今日课表桌面小部件桥接类，将小部件配置与 [CurriculumsWidgetProvider] 绑定。
 *
 * 继承 [BaseWidgetBridge] 的通用创建/权限检查能力，
 * 内部通过布局资源 ID 关联课表 Widget 的视图模板。
 */
@Suppress("unused")
class CurriculumsWidget(context: Context) : BaseWidgetBridge(context) {
    override fun getWidgetProviderClass(): Class<*> = CurriculumsWidgetProvider::class.java

    override fun getWidgetLayoutResId(): Int = R.layout.curriculums_widget
}