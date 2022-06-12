package com.tans.tmediaplayer

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

// first is data address, second is pts.
typealias ConsumerData = Pair<Long, Long>

typealias ProducerData = Long

internal class MediaRawDataPool(val values: List<Long>) {

    private val consumer: LinkedBlockingDeque<ConsumerData> by lazy {
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

    fun waitConsumer(): ProducerData {
        return producer.pollFirst(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
    }

    fun produce(value: ConsumerData) {
        consumer.addLast(value)
    }

    fun waitProducer(): ConsumerData {
        return consumer.pollFirst(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
    }

    fun consume(value: ProducerData) {
        producer.addLast(value)
    }

    companion object {
        const val PRODUCE_END = -1L
        const val PRODUCE_STOPPED = -2L
    }
}