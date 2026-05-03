package com.feifan.fuckingnjit.utils

import java.util.PriorityQueue

/**
 * 全局心跳调度总线
 *
 * 管理心跳唤醒的时间计算逻辑。支持基础节律和关键时间节点插队机制：
 * 当存在即将触发的关键节点时优先使用该节点时间，
 * 否则按基础间隔（默认 60 秒）安排下一次唤醒。
 */
object HeartbeatBus {
    /** 全局心跳广播 Action 字符串 */
    const val ACTION_GLOBAL_TICK = "com.feifan.fuckingnjit.ACTION_GLOBAL_TICK"

    /** 基础心跳间隔（毫秒） */
    const val HEARTBEAT_BASE = (60 * 1000).toLong()

    private var currentBaseInterval = HEARTBEAT_BASE

    /** 关键时间节点优先队列，最早触发的节点始终在队头 */
    private val criticalNodes = PriorityQueue<Long>()

    /**
     * 注册一个关键时间节点用于插队调度
     *
     * @param timestampMs 目标时间戳（毫秒），必须晚于当前时间且不重复
     */
    fun registerCriticalNode(timestampMs: Long) {
        val now = System.currentTimeMillis()
        if (timestampMs > now && !criticalNodes.contains(timestampMs)) {
            criticalNodes.offer(timestampMs)
        }
    }


    /**
     * 计算下一次心跳触发时间
     *
     * 取正常心跳时间和最近关键节点中较早的一个作为下次唤醒时刻。
     * 同时清理已过期的关键节点。
     *
     * @param now 当前时间戳（毫秒）
     * @return 下次应触发心跳的时间戳
     */
    fun calculateNextTickTime(now: Long): Long {
        val nextBaseTime = now + currentBaseInterval //下次正常的心跳时间

        // 清理已经过期或正在执行的关键节点
        while (criticalNodes.isNotEmpty() && criticalNodes.peek()!! <= now + 1000L) {
            criticalNodes.poll()
        }

        // 取出最近的一个关键节点
        val nextCriticalTime = criticalNodes.peek()

        // 下一次唤醒 = MIN(正常心跳时间, 最近的一个关键节点时间)
        return if (nextCriticalTime != null && nextCriticalTime < nextBaseTime) {
            nextCriticalTime
        } else {
            nextBaseTime
        }
    }
}