package com.tans.tmediaplayer

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

internal class MediaRawDataPool(val values: List<Long>) {

    private val consumer: LinkedBlockingDeque<Long> by lazy {
        LinkedBlockingDeque()
    }

    private val producer: LinkedBlockingDeque<Long> by lazy {
        LinkedBlockingDeque()
    }

    fun reset() {
        consumer.clear()
        producer.clear()
        producer.addAll(values)
    }

    fun waitConsumer(): Long {
        return producer.pollFirst(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
    }

    fun produce(value: Long) {
        consumer.addLast(value)
    }

    fun waitProducer(): Long {
        return consumer.pollFirst(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
    }

    fun consume(value: Long) {
        producer.addLast(value)
    }

    companion object {
        const val PRODUCE_END = -1L
        const val PRODUCE_RELEASED = -2L
    }
}