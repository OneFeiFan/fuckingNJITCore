package com.feifan.fuckingnjit.utils

import java.util.PriorityQueue

/**
 * 全局心跳总线：发布/订阅模式与关键节点插队机制
 */
object HeartbeatBus {
    const val ACTION_GLOBAL_TICK = "com.feifan.fuckingnjit.ACTION_GLOBAL_TICK"

    // 基础退火节律参数
    const val HEARTBEAT_BASE = (60 * 1000).toLong()

    private var currentBaseInterval = HEARTBEAT_BASE

    // 使用优先队列存储关键激活时间节点，确保最先发生的节点在队头
    private val criticalNodes = PriorityQueue<Long>()

//    fun setBaseInterval(intervalMs: Long) {
//        currentBaseInterval = intervalMs
//    }

    // 如果有新的关键激活时间点，可以加入criticalNodes
    fun registerCriticalNode(timestampMs: Long) {
        val now = System.currentTimeMillis()
        if (timestampMs > now && !criticalNodes.contains(timestampMs)) {
            criticalNodes.offer(timestampMs)
        }
    }


    // 计算下一次极其精准的唤醒时间
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