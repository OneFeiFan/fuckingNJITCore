package com.feifan.fuckingnjit.widget

import android.content.Context
import com.feifan.fuckingnjit.R

class DemoMainWidget(context: Context) :BaseWidgetBridge(context) {
    override fun getWidgetProviderClass(): Class<*> = CurriculumsWidgetProvider::class.java

    override fun getWidgetLayoutResId(): Int = R.layout.curriculums_widget
}