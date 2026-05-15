package com.lanrhyme.micyou.network

import com.lanrhyme.micyou.AudioPacketMessage
import com.lanrhyme.micyou.AudioPacketMessageOrdered
import com.lanrhyme.micyou.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 音频抖动缓冲区
 * 用于处理UDP乱序包，保持音频播放顺序
 * 设计目标：低延迟 + 正确的播放顺序
 */
class JitterBuffer(
    private val onAudioPacketReady: suspend (AudioPacketMessage) -> Unit
) {
    // 使用ConcurrentHashMap存储包，key为sequenceNumber
    private val buffer = ConcurrentHashMap<Int, AudioPacketMessageOrdered>()

    // 下一个期望播放的序列号
    private val expectedSequenceNumber = AtomicInteger(0)

    // 是否已初始化（收到第一个包）
    private val initialized = AtomicInteger(0)

    // 统计信息
    private val packetsReceived = AtomicInteger(0)
    private val packetsLost = AtomicInteger(0)
    private val packetsOutOfOrder = AtomicInteger(0)

    @Volatile
    private var isRunning = false

    // 协程作用域用于异步播放音频包
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 插入音频包
     */
    fun insert(packet: AudioPacketMessageOrdered) {
        if (!isRunning) return

        packetsReceived.incrementAndGet()

        // 初始化：第一个包设置期望序列号
        if (initialized.get() == 0) {
            expectedSequenceNumber.set(packet.sequenceNumber)
            initialized.set(1)
        }

        // 检查是否是过期包
        val currentExpected = expectedSequenceNumber.get()
        if (packet.sequenceNumber < currentExpected) {
            packetsOutOfOrder.incrementAndGet()
            return // 丢弃过期包
        }

        // 存储包
        buffer[packet.sequenceNumber] = packet

        // 尝试播放连续的包
        processBuffer()
    }

    /**
     * 处理缓冲区，播放连续的包
     */
    private fun processBuffer() {
        while (isRunning) {
            val seqNum = expectedSequenceNumber.get()
            val packet = buffer.remove(seqNum) ?: break // 没有期望的包，退出循环

            // 使用协程异步播放包
            val packetToPlay = packet
            scope.launch {
                try {
                    onAudioPacketReady(packetToPlay.audioPacket)
                } catch (e: Exception) {
                    Logger.e("JitterBuffer", "Error playing audio packet: ${e.message}")
                }
            }

            // 期望下一个包
            expectedSequenceNumber.incrementAndGet()
        }

        // 检查是否有包丢失（缓冲区中有更高序列号的包）
        val nextExpected = expectedSequenceNumber.get()
        val nextAvailable = buffer.keys.minOrNull()
        if (nextAvailable != null && nextAvailable > nextExpected) {
            // 有包丢失，跳过丢失的包
            val lostCount = nextAvailable - nextExpected
            packetsLost.addAndGet(lostCount)
            Logger.d("JitterBuffer", "Lost $lostCount packets, skipping to $nextAvailable")
            expectedSequenceNumber.set(nextAvailable)
            // 重新处理缓冲区
            processBuffer()
        }
    }

    /**
     * 启动缓冲区
     */
    fun start() {
        isRunning = true
        Logger.i("JitterBuffer", "Jitter buffer started")
    }

    /**
     * 停止缓冲区
     */
    fun stop() {
        isRunning = false
        buffer.clear()
        expectedSequenceNumber.set(0)
        initialized.set(0)
        // 注意：不取消scope，因为它可能被其他组件使用
        Logger.i("JitterBuffer", "Jitter buffer stopped")
    }

    /**
     * 获取统计信息
     */
    fun getStats(): String {
        return "received=${packetsReceived.get()}, lost=${packetsLost.get()}, outOfOrder=${packetsOutOfOrder.get()}, buffer=${buffer.size}"
    }
}
