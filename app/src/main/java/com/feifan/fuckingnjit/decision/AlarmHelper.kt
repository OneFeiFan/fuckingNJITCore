package com.feifan.fuckingnjit.decision

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast

/**
 * 闹钟辅助工具
 *
 * 封装了通过 ACTION_SET_ALARM 调起系统闹钟 App 的完整逻辑。
 * UI 层只需拿到 DashboardResponse 中的 [AlarmInfo]，然后调用本工具即可一键设闹钟。
 *
 * 设计原则：
 *   - 不使用 AlarmManager 直接设闹钟（避免权限问题和用户信任问题）
 *   - 通过 Intent 打开系统闹钟页面，时间预填好，用户点一下确认即可
 *   - 不做 UI 交互，纯工具类，由前端 Activity/Fragment 触发
 */
@Suppress("unused")
object AlarmHelper {

    /**
     * 调起系统闹钟 App 设置闹钟
     *
     * 使用方式（UI 层示例代码）：
     * ```kotlin
     * // 从 dashboardJson 中取出 alarmInfo
     * val alarmInfo = dashboardObj.getObject("alarmInfo", AlarmInfo::class.java)
     * if (alarmInfo?.canSetAlarm == true) {
     *     AlarmHelper.setSystemAlarm(context, alarmInfo)
     * }
     * ```
     *
     * @param context  Context（建议用 Activity Context，确保 startActivityForResult 有宿主）
     * @param alarmInfo 由 DecisionEngine.buildAlarmInfo() 产出的闹钟信息
     * @return true=已成功发出Intent / false=参数异常或无法处理
     */
    fun setSystemAlarm(context: Context, alarmInfo: AlarmInfo): Boolean {
        // 前置校验：canSetAlarm 必须为 true
        if (!alarmInfo.canSetAlarm) {
            Toast.makeText(
                context,
                alarmInfo.reason.ifEmpty { "当前不允许设置闹钟" },
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        // 时间范围合理性检查
        val hour = alarmInfo.suggestedWakeUpHour.coerceIn(0, 23)
        val minute = alarmInfo.suggestedWakeUpMinute.coerceIn(0, 59)

        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, alarmInfo.alarmLabel)
                // SKIP_UI=false：打开系统闹钟页面让用户确认，而不是静默设置
                // 这样用户的闹钟列表里能看到这个闹钟，符合用户心理模型
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                // 使用 NEW_TASK 标志，因为可能从非 Activity Context 调用
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "无法打开系统闹钟：${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    /**
     * 快捷方法：直接通过小时和分钟调起系统闹钟（不经过 AlarmInfo）
     *
     * 适用于 UI 层已有独立的时间选择器、不需要走决策引擎的场景
     *
     * @param context   Context
     * @param hour      小时（0~23）
     * @param minute    分钟（0~59）
     * @param label     闹钟标签文案（可选）
     */
    fun setSystemAlarmDirect(
        context: Context,
        hour: Int,
        minute: Int,
        label: String = "FuckingNJIT 起床提醒"
    ): Boolean {
        val info = AlarmInfo(
            suggestedWakeUpHour = hour.coerceIn(0, 23),
            suggestedWakeUpMinute = minute.coerceIn(0, 59),
            alarmType = "manual",
            alarmLabel = label,
            canSetAlarm = true
        )
        return setSystemAlarm(context, info)
    }

    /**
     * 检查设备是否支持 ACTION_SET_ALARM
     *
     * 部分定制 ROM 或特殊设备可能没有标准的闹钟 App，
     * 调用前可先做此检查来决定是否展示"设闹钟"按钮
     */
    fun isAlarmAvailable(context: Context): Boolean {
        return Intent(AlarmClock.ACTION_SET_ALARM).resolveActivity(context.packageManager) != null
    }
}
