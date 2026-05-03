package com.feifan.fuckingnjit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.feifan.fuckingnjit.R
import com.feifan.fuckingnjit.utils.DailyCourseSlot
import com.feifan.fuckingnjit.utils.EduScheduleConfig
import com.feifan.fuckingnjit.utils.HeartbeatBus
import com.feifan.fuckingnjit.utils.TodayScheduleManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 课程表桌面小部件 Provider
 *
 * 负责渲染今日剩余课程到桌面小部件，支持窄版（2条课程）和宽版（4条课程）两种布局。
 * 通过全局心跳广播和下课时间节点注册机制实现自动刷新。
 */
class CurriculumsWidgetProvider : AppWidgetProvider() {

    companion object {
        private val TAG = "CurriculumsWidgetProvider"
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
    }

    /**
     * 更新所有小部件实例的 UI 内容
     *
     * 根据布局尺寸选择展示数量，填充日期时间、课程信息，
     * 并注册下一次关键时间节点的自动刷新。
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 拿数据并计算下一次自动更新的时间点
        val validList = TodayScheduleManager.getRemainingCoursesForWidget()
        calculateAndRegisterNextCriticalNode(validList)

        for (widgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val isWideMode = minWidth > 200
            val maxCount = if (isWideMode) 4 else 2

            val layoutId =
                if (isWideMode) R.layout.curriculums_widget_wide else R.layout.curriculums_widget
            val remoteViews = RemoteViews(context.packageName, layoutId)

            // 设置头部日期时间
            val now = java.time.LocalDateTime.now()
            remoteViews.setTextViewText(R.id.month_id, now.format(CHINA_DATE_FORMATTER))
            remoteViews.setTextViewText(
                R.id.week_id,
                now.dayOfWeek.getDisplayName(
                    CHINA_WEEK_FORMATTER.first,
                    CHINA_WEEK_FORMATTER.second
                )
            )

            // 恢复点击背景 FORCE_REFRESH 的逻辑
            val refreshIntent = Intent(context, CurriculumsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                putExtra("FORCE_REFRESH", true)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, widgetId, refreshIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val rootId = if (isWideMode) R.id.wide_widget else R.id.small_widget
            remoteViews.setOnClickPendingIntent(rootId, pendingIntent)

            if (validList.isEmpty()) {
                // 无课状态
                remoteViews.setViewVisibility(R.id.empty_view, View.VISIBLE)
                remoteViews.setViewVisibility(R.id.courses_container, View.GONE)
            } else {
                // 有课状态
                remoteViews.setViewVisibility(R.id.empty_view, View.GONE)
                remoteViews.setViewVisibility(R.id.courses_container, View.VISIBLE)

                try {
                    val showList = validList.take(maxCount) // 截取能显示的课程

                    // A. 填充有数据的块
                    for (i in showList.indices) {
                        val course = showList[i]
                        val timeColor = if (i > 0 && isConflict(
                                course,
                                showList[i - 1]
                            )
                        ) Color.RED else Color.WHITE
                        fillCourseBlock(remoteViews, i, course, timeColor)
                    }

                    // B. 隐藏多余的块，防止残留复用
                    for (i in showList.size until 4) {
                        if (i < IDS_BLOCK.size) {
                            remoteViews.setViewVisibility(IDS_BLOCK[i], View.GONE)
                        }
                    }

                    // C. 针对宽版的特殊处理：如果只有左侧有课(1-2节)，隐藏中间的竖线让UI更纯净
                    if (isWideMode) {
                        if (showList.size <= 2) {
                            remoteViews.setViewVisibility(R.id.vertical_divider, View.GONE)
                        } else {
                            remoteViews.setViewVisibility(R.id.vertical_divider, View.VISIBLE)
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    // 抛异常就显示无课
                    remoteViews.setViewVisibility(R.id.empty_view, View.VISIBLE)
                    remoteViews.setViewVisibility(R.id.courses_container, View.GONE)
                }
            }

            // 应用更新
            appWidgetManager.updateAppWidget(widgetId, remoteViews)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val appWidgetManager = AppWidgetManager.getInstance(context)
        Log.i(TAG, "收到广播$action")
        // 处理点击强制刷新
        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            if (intent.getBooleanExtra("FORCE_REFRESH", false)) {
                TodayScheduleManager.clearCache()
                val extrasIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                if (extrasIds != null) {
                    onUpdate(context, appWidgetManager, extrasIds)
                }
            }
        }

        // 处理新版引入的全局心跳滴答 (到点下课自动刷新 UI)
        if (action == HeartbeatBus.ACTION_GLOBAL_TICK) {
            val componentName = ComponentName(context, CurriculumsWidgetProvider::class.java)
            val allIds = appWidgetManager.getAppWidgetIds(componentName)
            if (allIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, allIds)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 尺寸变化（比如用户拉伸 widget）立即触发重绘
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun fillCourseBlock(
        rv: RemoteViews,
        index: Int,
        slot: DailyCourseSlot,
        timeColor: Int
    ) {
        if (index >= IDS_BLOCK.size) return

        rv.setViewVisibility(IDS_BLOCK[index], View.VISIBLE)
        rv.setTextViewText(IDS_NAME[index], slot.courseName)
        rv.setTextViewText(IDS_LOC[index], slot.classroom)

        val timeStr = EduScheduleConfig.getDisplayTime(slot.startNode, slot.step)
        rv.setTextViewText(IDS_TIME[index], timeStr)
        rv.setTextColor(IDS_TIME[index], timeColor)
    }

    private fun isConflict(
        current: DailyCourseSlot,
        prev: DailyCourseSlot
    ): Boolean {
        return current.startNode <= prev.startNode
    }

    /**
     * 计算下一次课程的开始/结束时间点，并向 HeartbeatBus 注册。
     */
    private fun calculateAndRegisterNextCriticalNode(courses: List<DailyCourseSlot>) {
        if (courses.isEmpty()) return

        val nowTime = LocalTime.now()
        var nextCriticalLocalTime: LocalTime? = null

        for (slot in courses) {
            if (nowTime.isBefore(slot.startTime)) {
                nextCriticalLocalTime = slot.startTime
                break
            } else if (nowTime.isBefore(slot.endTime)) {
                nextCriticalLocalTime = slot.endTime
                break
            }
        }

        nextCriticalLocalTime?.let { targetTime ->
            val timestampMs = targetTime.atDate(LocalDate.now())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            // 延后 1 秒触发，确保时间完全跨越节点判定
            HeartbeatBus.registerCriticalNode(timestampMs + 1000L)
        }
    }
}