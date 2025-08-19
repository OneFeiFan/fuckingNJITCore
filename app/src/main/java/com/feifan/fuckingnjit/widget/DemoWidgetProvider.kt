// 定义包名，表示该文件所在的包路径
package com.feifan.fuckingnjit.widget

// 导入所需的Android类库

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.database.UserData
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.Tools
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale


// 定义DemoWidgetProvider类，继承自AppWidgetProvider
class DemoWidgetProvider : AppWidgetProvider() {

    // 伴生对象，包含静态方法和属性
    companion object {
//        private const val UPDATE_INTERVAL_MINUTES = 45L
//        private const val WORKER_TAG = "widget_update_work"
        // 更新所有小部件的方法
        fun updateWidgets(context: Context) {
            // 创建Intent对象，指定接收者为DemoWidgetProvider
            val intent = Intent(context, DemoWidgetProvider::class.java)
            // 设置动作为更新小部件
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            // 获取所有该小部件的ID
            val widgetIDs = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, DemoWidgetProvider::class.java))
            // 将小部件ID数组放入Intent中
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIDs)
            // 发送广播
            context.sendBroadcast(intent)
        }

        // 获取RemoteViews对象的方法
        fun getRemoteViews(context: Context, widgetId: Int): RemoteViews {
            // 创建RemoteViews对象，指定布局文件
            val remoteViews = RemoteViews(context.packageName, R.layout.widget)
            val onClick =
                Intent().setClass(context, DemoWidgetProvider::class.java).setAction("CLICK_ACTION")
                    .putExtra("WIDGET_ID", widgetId)
            //为布局文件中的按钮设置点击监听
            val pendingIntent =
                PendingIntent.getBroadcast(context, 0, onClick, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            remoteViews.setOnClickPendingIntent(R.id.small_widget, pendingIntent)
            // 获取当前日期和时间
            val currentDate = LocalDate.now()
            val currentTime = LocalTime.now()
            // 格式化日期和时间
            val dateFormatter = DateTimeFormatter.ofPattern("M.d", Locale.CHINA)
            val monthDayText = currentDate.format(dateFormatter)
            val weekDayText = currentDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
            // 设置月份和周几
            remoteViews.setTextViewText(R.id.month_id, monthDayText)
            remoteViews.setTextViewText(R.id.week_id, weekDayText)

           val user: UserData?
            try {
                user = Manager.getUserManager()?.getCurrentUser()
            }catch (e: Exception){
                e.printStackTrace()
                return remoteViews
            }
            val curriculums = user?.curriculums
            if (curriculums == null||curriculums == "") {
                println("没有课表")
                return remoteViews
            }
            val curriculumsObject = JSONObject.parseArray(curriculums) as List<List<List<String>>>

                   // 使用 fastjson 解析 JSON 数组字符串
            val list: List<HashMap<String, String>> = Tools.parseCourseSchedule(JSONArray.parseArray(
                curriculumsObject[Manager.getCurrentWeek()][Manager.getTimeManager().todayWeekIndex()].toString()
            ))
            // 过滤出还没结束的课程
            var sortedList: List<HashMap<String, String>> = emptyList<HashMap<String, String>>()
            try {
                val filteredList = list.filter {
                    val endTime = LocalTime.parse(it["time"]?.split("-")?.get(1))
                    currentTime.isBefore(endTime)
                }
                // 按时间排序
                sortedList = filteredList.sortedBy {
                    val startTime = LocalTime.parse(it["time"]?.split("-")?.get(0))
                    startTime
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return remoteViews
            }
            if (sortedList.isEmpty()) {
                return remoteViews
            }
            // 获取前四个课程
            val coursesToShow = sortedList.take(2)
            // 设置组件的文本
            for (i in 0 until 2) {
                val course = coursesToShow.getOrNull(i)
                val courseName = course?.get("course_name") ?: ""
                val time = course?.get("time") ?: ""
                val location = course?.get("location") ?: ""
                remoteViews.setTextViewText(
                    context.resources.getIdentifier(
                        "course_id_${i + 1}",
                        "id",
                        context.packageName
                    ), courseName
                )
                remoteViews.setTextViewText(
                    context.resources.getIdentifier(
                        "time_id_${i + 1}",
                        "id",
                        context.packageName
                    ), time
                )
                remoteViews.setTextViewText(
                    context.resources.getIdentifier(
                        "location_id_${i + 1}",
                        "id",
                        context.packageName
                    ), location
                )
            }

            // 返回配置好的RemoteViews
            return remoteViews
        }


        // 更新小部件UI的方法
        fun updateWidgetUI(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            appWidgetManager.updateAppWidget(widgetId, getRemoteViews(context, widgetId))
        }

        // 添加初始化定时任务的方法
//        fun schedulePeriodicUpdate(context: Context) {
//            val workManager = WorkManager.getInstance(context.applicationContext)
//
//            // 创建周期性工作请求
//            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
//                UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
//            )
//                .addTag(WORKER_TAG)
//                .build()
//
//            // 使用唯一工作序列确保只有一个定时任务
//            workManager.enqueueUniquePeriodicWork(
//                "widget_periodic_update",
//                ExistingPeriodicWorkPolicy.KEEP, // 如果已存在则保持原有任务
//                request
//            )
//        }

        // 取消定时任务的方法
//        fun cancelPeriodicUpdate(context: Context) {
//            WorkManager.getInstance(context.applicationContext)
//                .cancelAllWorkByTag(WORKER_TAG)
//        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 调用父类方法
        super.onReceive(context, intent)
        if (intent.action.equals("CLICK_ACTION")) {
            val widgetId = intent.getIntExtra("WIDGET_ID", AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val manager = AppWidgetManager.getInstance(context)
                manager.updateAppWidget(widgetId, getRemoteViews(context, widgetId))
            }
        } else if (intent.action.equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE)) {
            // 获取所有需要更新的小部件ID
            val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            // 遍历所有需要更新的小部件ID
            if (widgetIds != null) {
                for (widgetId: Int in widgetIds) {
                    // 更新每个小部件的UI
                    updateWidgetUI(
                        context,
                        AppWidgetManager.getInstance(context),
                        widgetId
                    )
                }
            }
        }
//        manager.updateAppWidget(ComponentName(context, CalculateProvider1::class.java), remoteView)
    }

    // 重写onUpdate方法，当小部件需要更新时调用
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 调用父类方法
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // 遍历所有需要更新的小部件ID
        for (widgetId: Int in appWidgetIds) {
            // 更新每个小部件的UI
            updateWidgetUI(context, appWidgetManager, widgetId)
        }
        Toast.makeText(context, "小部件已经创建成功", Toast.LENGTH_SHORT).show()
    }

    // 重写onDeleted方法，当小部件被删除时调用
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // 调用父类方法
        super.onDeleted(context, appWidgetIds)

    }

    override fun onEnabled(context: Context) {
//        schedulePeriodicUpdate(context)
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
//        cancelPeriodicUpdate(context)
        super.onDisabled(context)
        Manager.unregisterTimeReceiver()
        //关闭服务
//        val intent = Intent(context, WidgetService::class.java)
//        context.stopService(intent)
    }
}
