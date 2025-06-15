package com.feifan.fuckingnjit.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // 调用原有的更新方法
        DemoWidgetProvider.updateWidgets(applicationContext)
        return Result.success()
    }
}