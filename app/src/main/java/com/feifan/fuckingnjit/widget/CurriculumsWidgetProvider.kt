package com.feifan.fuckingnjit.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.utils.CourseTimeUtils
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import com.feifan.fuckingnjit.utils.database.BaseDataBoxUtils
import com.feifan.fuckingnjit.utils.database.UserBoxUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.Observer


class CurriculumsWidgetProvider : AppWidgetProvider() {

    companion object {
        private val CHINA_DATE_FORMATTER = DateTimeFormatter.ofPattern("M.d hh:mm a", Locale.CHINA)
        private val CHINA_WEEK_FORMATTER = TextStyle.FULL to Locale.CHINA

        private val IDS_BLOCK = intArrayOf(
            R.id.course_block_1,
            R.id.course_block_2,
            R.id.course_block_3,
            R.id.course_block_4
        )
        private val IDS_NAME =
            intArrayOf(R.id.course_id_1, R.id.course_id_2, R.id.course_id_3, R.id.course_id_4)
        private val IDS_LOC = intArrayOf(
            R.id.location_id_1,
            R.id.location_id_2,
            R.id.location_id_3,
            R.id.location_id_4
        )
        private val IDS_TIME =
            intArrayOf(R.id.time_id_1, R.id.time_id_2, R.id.time_id_3, R.id.time_id_4)

        // --- 2. 缓存策略优化 ---
        // 原始总数据（用于周数切换时重新计算）
//        private var cachedAllCurriculumData: List<Course>? = null


        // 外部调用的 Observer
        val observer: Observer = Observer { _, _ ->
            // 收到数据变更通知，彻底清空，下次强制重新加载
            TodayScheduleManager.clearCache()
        }

        fun updateWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)
            val componentName =
                ComponentName(context.applicationContext, CurriculumsWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (widgetIds.isNotEmpty()) {
                val updateIntent = Intent(
                    context.applicationContext,
                    CurriculumsWidgetProvider::class.java
                ).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.applicationContext.sendBroadcast(updateIntent)
            }
        }

        @SuppressLint("ServiceCast")
        fun getRemoteViews(context: Context, widgetId: Int): RemoteViews {
            // 1. 防御性初始化
            ensureInitialized(context)

            // 2. Alarm 防杀与保活 (防御性注册)
            ensureAlarmRegistered(context)

            // 4. 获取布局配置
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val isWideMode = minWidth > 200
            val maxCount = if (isWideMode) 4 else 2

            val layoutId =
                if (isWideMode) R.layout.curriculums_widget_wide else R.layout.curriculums_widget
            val remoteViews = RemoteViews(context.packageName, layoutId)

            // 5. 基础 UI 设置
            setupBaseUI(context, remoteViews, widgetId, isWideMode)

            // 6. 从队列中移除已过期的课程
            val validList = TodayScheduleManager.getRemainingCoursesForWidget()

            if (validList.isEmpty()) {
                showEmptyState(remoteViews)
                return remoteViews
            }

            // 7. 渲染课程列表
            try {
                // 取出要显示的条目 (最多 maxCount)
                val showList = validList.take(maxCount)
                for (i in showList.indices) {
                    val course = showList[i]
                    val timeColor =
                        if (i > 0 && isConflict(course, showList[i - 1])) Color.RED else Color.WHITE

                    fillCourseBlock(remoteViews, i, course, timeColor)
                }

                // 隐藏多余的 Block (防止复用导致的残留)
                for (i in showList.size until 4) {
                    if (i < IDS_BLOCK.size) {
                        remoteViews.setViewVisibility(IDS_BLOCK[i], View.GONE)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showEmptyState(remoteViews)
            }

            return remoteViews
        }

        private fun ensureInitialized(context: Context) {
            if (!UserBoxUtils.isInitialized()) {
                UserBoxUtils.init(context)
            }
            if (!BaseDataBoxUtils.isInitialized()) {
                BaseDataBoxUtils.init(context)
            }
        }

        private fun ensureAlarmRegistered(context: Context) {
            val currentWeek = TodayScheduleManager.getCurrentWeek()
            // 只有在学期周数范围内才注册
            if (currentWeek !in 0..19) return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val updateIntent = Intent(context, CurriculumsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }

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

        private fun setupBaseUI(
            context: Context,
            rv: RemoteViews,
            widgetId: Int,
            isWideMode: Boolean
        ) {
            // 构造点击 Intent
            val clickIntent = Intent(context, CurriculumsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

                //必须放入 ID 数组，否则 onUpdate 不会被调用
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))

                //添加自定义标记
                putExtra("FORCE_REFRESH", true)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId, // 使用 widgetId 作为 requestCode 区分
                clickIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val rootId = if (isWideMode) R.id.wide_widget else R.id.small_widget
            rv.setOnClickPendingIntent(rootId, pendingIntent)

            // 时间显示
            LocalDateTime.now().run {
                rv.setTextViewText(R.id.month_id, format(CHINA_DATE_FORMATTER))
                rv.setTextViewText(
                    R.id.week_id,
                    dayOfWeek.getDisplayName(
                        CHINA_WEEK_FORMATTER.first,
                        CHINA_WEEK_FORMATTER.second
                    )
                )
            }

            // 重置容器可见性
            rv.setViewVisibility(R.id.empty_view, View.GONE)
            rv.setViewVisibility(R.id.courses_container, View.VISIBLE)
        }

        private fun isConflict(
            current: TodayScheduleManager.DailyCourseSlot,
            prev: TodayScheduleManager.DailyCourseSlot
        ): Boolean {
            return current.startNode <= prev.startNode
        }

        private fun fillCourseBlock(
            rv: RemoteViews,
            index: Int,
            slot: TodayScheduleManager.DailyCourseSlot,
            timeColor: Int
        ) {
            if (index >= IDS_BLOCK.size) return

            rv.setViewVisibility(IDS_BLOCK[index], View.VISIBLE)
            // 属性名根据 Slot 定义做细微调整
            rv.setTextViewText(IDS_NAME[index], slot.courseName)
            rv.setTextViewText(IDS_LOC[index], slot.classroom)

            // 依然可以完美复用 CourseTimeUtils 的展示逻辑
            val timeStr = CourseTimeUtils.getDisplayTime(slot.startNode, slot.step)
            rv.setTextViewText(IDS_TIME[index], timeStr)
            rv.setTextColor(IDS_TIME[index], timeColor)
        }

        private fun showEmptyState(rv: RemoteViews) {
            rv.setViewVisibility(R.id.empty_view, View.VISIBLE)
            rv.setViewVisibility(R.id.courses_container, View.GONE)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            println("Updating widget: $appWidgetId")
            appWidgetManager.updateAppWidget(appWidgetId, getRemoteViews(context, appWidgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        // 立即使用新尺寸重新生成视图
        val remoteViews = getRemoteViews(context, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)

        println("Widget resized: Options changed for ID $appWidgetId")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        println("Received intent: $action")

        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            if (intent.getBooleanExtra("FORCE_REFRESH", false)) {
                TodayScheduleManager.clearCache()
            }
            val extrasIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)

            // 如果 Intent 里没有 ID (说明是 Alarm 触发的，或者是我们的 updateWidgets 广播漏了 ID)
            if (extrasIds == null || extrasIds.isEmpty()) {
                println("Update action detected without IDs. Fetching all widgets...")

                // 手动获取当前所有活跃的小部件 ID
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, CurriculumsWidgetProvider::class.java)
                val allIds = appWidgetManager.getAppWidgetIds(componentName)

                // 手动调用 onUpdate 进行全量更新
                onUpdate(context, appWidgetManager, allIds)
            }
        }

        super.onReceive(context, intent)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 取消 Alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val updateIntent = Intent(context, CurriculumsWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}