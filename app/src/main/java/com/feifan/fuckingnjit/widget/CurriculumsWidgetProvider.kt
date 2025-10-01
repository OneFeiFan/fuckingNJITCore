// 定义包名，表示该文件所在的包路径
package com.feifan.fuckingnjit.widget

// 导入所需的Android类库
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.TypeReference
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.database.UserData
import com.feifan.fuckingnjit.utils.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.Manager
import com.feifan.fuckingnjit.utils.TimeManager
import com.feifan.fuckingnjit.utils.Tools
import com.feifan.fuckingnjit.utils.UserBoxUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.Observable
import java.util.Observer


// 定义CurriculumsWidgetProvider类，继承自AppWidgetProvider
class CurriculumsWidgetProvider : AppWidgetProvider() {

    // 伴生对象，包含静态方法和属性
    companion object {
        private val CHINA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("M.d hh:mm a", Locale.CHINA) // 日期格式化器
        private val CHINA_WEEK_FORMATTER = TextStyle.FULL to Locale.CHINA // 星期格式化器
//        private val FILTER = IntentFilter(Intent.ACTION_TIME_TICK)

        private var cachedSemesterStartDate: Long? = null
        private var cachedCurrentWeek = -1
        private var lastUpdateDay: Int = -1
        private var cachedCurriculumData: List<List<List<String>>>? = null
        private var cachedWeekIndex: Int = -1

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                println("Time ticked")
                updateWidgets(context)
            }
        }

        val observer: Observer = object : Observer {
            @Deprecated("Deprecated in Java")
            override fun update(o: Observable?, arg: Any?) {
                clearCurriculumCache()
            }
        }

        // 更新所有小部件的方法
        fun updateWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context.applicationContext, CurriculumsWidgetProvider::class.java)
            )

            if (widgetIds.isNotEmpty()) {
                val updateIntent =
                    Intent(
                        context.applicationContext,
                        CurriculumsWidgetProvider::class.java
                    ).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                    }
                context.applicationContext.sendBroadcast(updateIntent)
            }
        }

        private fun clearCurriculumCache() {
            cachedSemesterStartDate = null
            cachedCurrentWeek = -1
            lastUpdateDay = -1
            cachedCurriculumData = null
            cachedWeekIndex = -1
        }

        private fun getCurriculumData(curriculums: String): List<List<List<String>>>? {
            return try {
                if (cachedCurriculumData == null) {
                    cachedCurriculumData = JSON.parseObject<List<List<List<String>>>>(
                        curriculums,
                        object : TypeReference<List<List<List<String>>>>() {}.type
                    )
                }
                cachedCurriculumData
            } catch (e: Exception) {
                println("解析课表失败")
                println(e.message)
                null
            }
        }

        private fun getCourseList(
            context: Context,
            curriculums: String
        ): List<HashMap<String, String>> {
            val weekIndex = Manager.getTimeManager().todayWeekIndex()

            // 如果周数和星期索引没变，直接返回缓存数据
            if (weekIndex == cachedWeekIndex && cachedCurriculumData != null) {
                return Tools.parseCourseSchedule(
                    cachedCurriculumData!![cachedCurrentWeek][weekIndex]
                )
            }

            // 否则重新解析并更新缓存
            val curriculumsObject = getCurriculumData(curriculums) ?: return emptyList()
            cachedWeekIndex = weekIndex

            return Tools.parseCourseSchedule(
                curriculumsObject[cachedCurrentWeek][weekIndex]
            )
        }
//fun saveWidgetAsImage(context: Context, widgetView: RemoteViews): Boolean {
//    try {
//        // 1. 获取小部件布局的实际视图
//        val widget = widgetView.apply(context, null)
//
//        // 2. 测量视图大小
//        widget.measure(
//            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
//            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
//        )
//
//        // 3. 布局视图
//        widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
//
//        // 4. 创建位图
//        val bitmap = Bitmap.createBitmap(
//            widget.measuredWidth,
//            widget.measuredHeight,
//            Bitmap.Config.ARGB_8888
//        )
//
//        // 5. 将视图绘制到位图上
//        val canvas = Canvas(bitmap)
//        widget.draw(canvas)
//
//        // 6. 保存图片到应用目录
//        val fileName = "widget_${System.currentTimeMillis()}.png"
//        val outputDir = context.filesDir
//        val outputFile = File(outputDir, fileName)
//
//        FileOutputStream(outputFile).use { out ->
//            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
//            out.flush()
//        }
//
//        return true
//    } catch (e: Exception) {
//        e.printStackTrace()
//        return false
//    }
//}

        // 获取RemoteViews对象的方法
        @SuppressLint("ServiceCast")
        fun getRemoteViews(context: Context, widgetId: Int): RemoteViews {
            // 创建RemoteViews对象，指定布局文件
            if (!UserBoxUtils.isInitialized()) {
                println("UserBoxUtils not initialized")
                UserBoxUtils.init(context)
            }
            if (!BaseDataBoxUtils.isInitialized()) {
                println("BaseDataBoxUtils not initialized")
                BaseDataBoxUtils.init(context)
            }
            // 带缓存的学期开始日期获取
            if (cachedSemesterStartDate == null) {
                cachedSemesterStartDate = LocalDate.parse(Manager.getSemesterStartDate())
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }

            // 带日级别缓存的周数计算
            val todayDay = LocalDate.now().dayOfYear
            if (lastUpdateDay != todayDay) {
                cachedCurrentWeek =
                    TimeManager.getInstance().calculateCurrentWeek(cachedSemesterStartDate!!)
                lastUpdateDay = todayDay
            }

            // 只在当前周数有效(非-1)且不超过19周时注册时间监听器
            if (cachedCurrentWeek in 0..19) {
                try {
//                    if(!XiaomiUtilities.isFlyme) {
//                        context.applicationContext.registerReceiver(receiver, FILTER)
//                    }else {
                    val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
                    val widgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(
                            context.applicationContext,
                            CurriculumsWidgetProvider::class.java
                        )
                    )
                    widgetIds.forEach {
                        println(it)
                    }
                    if (widgetIds.isNotEmpty()) {
                        val updateIntent =
                            Intent(
                                context,
                                CurriculumsWidgetProvider::class.java
                            ).apply {
                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                            }
                        val alarmManager =
                            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                        // 直接使用 PendingIntent 调用 updateWidgets 函数
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            0,
                            updateIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        println("PendingIntent created")
                        // 设置每分钟触发一次的闹钟
                        alarmManager.setRepeating(
                            AlarmManager.RTC,
                            System.currentTimeMillis(),
                            60 * 1000,
                            pendingIntent
                        )
                    }
//                    }

                    println("Receiver registered")
                } catch (e: IllegalArgumentException) {
                    // 已经注册（可能由其他路径触发）
                    println("Receiver already registered")
                    println(e.message)
                }
            }
            val remoteViews = RemoteViews(context.packageName, R.layout.curriculums_widget)
            remoteViews.setViewVisibility(R.id.empty_view, View.GONE)
            remoteViews.setViewVisibility(R.id.course_block_1, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.course_block_2, View.VISIBLE)
            val onClick =
                Intent().setClass(context, CurriculumsWidgetProvider::class.java)
                    .setAction("CLICK_ACTION")
                    .putExtra("WIDGET_ID", widgetId)
            //为布局文件中的按钮设置点击监听
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    onClick,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            remoteViews.setOnClickPendingIntent(R.id.small_widget, pendingIntent)


            LocalDateTime.now().run {
                remoteViews.apply {
                    setTextViewText(R.id.month_id, format(CHINA_DATE_FORMATTER))//设置月份
                    setTextViewText(
                        R.id.week_id,
                        dayOfWeek.getDisplayName(
                            CHINA_WEEK_FORMATTER.first,
                            CHINA_WEEK_FORMATTER.second
                        )
                    )
                }
            }

            val user = try {
                UserBoxUtils.getUserById(BaseDataBoxUtils.getCurrentUserId()) ?: UserData()
            } catch (e: Exception) {
                e.printStackTrace()
                return remoteViews
            }

            val curriculums = user.curriculums.getString("validTimeCourses")
            if (curriculums == null || curriculums == "") {
                remoteViews.setViewVisibility(R.id.empty_view, View.VISIBLE)
                remoteViews.setViewVisibility(R.id.course_block_1, View.GONE)
                remoteViews.setViewVisibility(R.id.course_block_2, View.GONE)
                println("没有课表")
                return remoteViews
            }

            // 使用 fastjson 解析 JSON 数组字符串
            val list: List<HashMap<String, String>> = getCourseList(context, curriculums)

            // 过滤出还没结束的课程
            val sortedList: List<HashMap<String, String>> = try {
                val now = LocalTime.now()
                list.filter {
                    it["time"]?.split("-")?.get(1)?.let { end ->
                        now.isBefore(LocalTime.parse(end))
                    } ?: false
                }.sortedBy {
                    it["time"]?.split("-")?.get(0)?.let { start ->
                        LocalTime.parse(start)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return remoteViews
            }

            if (sortedList.isEmpty()) {
                remoteViews.setViewVisibility(R.id.empty_view, View.VISIBLE)
                remoteViews.setViewVisibility(R.id.course_block_1, View.GONE)
                remoteViews.setViewVisibility(R.id.course_block_2, View.GONE)
                return remoteViews
            }

            val courseIds = arrayOf(R.id.course_id_1, R.id.course_id_2)
            val timeIds = arrayOf(R.id.time_id_1, R.id.time_id_2)
            val locationIds = arrayOf(R.id.location_id_1, R.id.location_id_2)
            // 获取前2个课程
            val coursesToShow = sortedList.take(2)

            // 设置组件的文本
            for (i in 0 until 2) {
                val course = coursesToShow.getOrNull(i)
                val courseName = course?.get("course_name") ?: ""
                val time = course?.get("time") ?: ""
                val location = course?.get("location") ?: ""
                remoteViews.setTextViewText(courseIds[i], courseName)
                remoteViews.setTextViewText(timeIds[i], time)
                remoteViews.setTextViewText(locationIds[i], location)
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        println("Received intent: ${intent.action}")
        // 调用父类方法
        if (intent.action.equals("CLICK_ACTION")) {
            val widgetId = intent.getIntExtra("WIDGET_ID", AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                clearCurriculumCache()
                println("Widget clicked")
                AppWidgetManager.getInstance(context)
                    .updateAppWidget(widgetId, getRemoteViews(context, widgetId))
            }
        } else if (intent.action.equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE)) {
            // 获取所有需要更新的小部件ID
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(
                    context,
                    this::class.java
                )
            )
            // 遍历所有需要更新的小部件ID
            if (widgetIds != null) {
                for (widgetId: Int in widgetIds) {
                    // 更新每个小部件的UI
                    println("Update widget $widgetId")
                    AppWidgetManager.getInstance(context)
                        .updateAppWidget(widgetId, getRemoteViews(context, widgetId))
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        try {
            context.applicationContext.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            println("Receiver not registered")
            println(e.message)
        }
    }
}
