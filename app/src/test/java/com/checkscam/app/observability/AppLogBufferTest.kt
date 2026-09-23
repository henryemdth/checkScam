package com.checkscam.app.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppLogBufferTest {

    private val buffer = AppLogBuffer()

    @Test
    fun startsEmpty() {
        assertTrue(buffer.entries.value.isEmpty())
    }

    @Test
    fun log_appendsInOrder() {
        buffer.log(LogStage.STT, "hola")
        buffer.log(LogStage.CLASSIFIER, "raw json")
        buffer.log(LogStage.ALERTS, "DISPATCHED")
        buffer.log(LogStage.NOTIFICATIONS, "[com.gmail] transferencia")

        val entries = buffer.entries.value
        assertEquals(4, entries.size)
        assertEquals(LogStage.STT, entries[0].stage)
        assertEquals("hola", entries[0].message)
        assertEquals(LogStage.CLASSIFIER, entries[1].stage)
        assertEquals(LogStage.ALERTS, entries[2].stage)
        assertEquals(LogStage.NOTIFICATIONS, entries[3].stage)
        assertTrue(entries[0].timestampEpochMs <= entries[3].timestampEpochMs)
    }

    @Test
    fun exceedsCapacity_dropsOldest() {
        val small = AppLogBuffer(capacity = 5)
        small.log(LogStage.SYSTEM, "0")
        small.log(LogStage.SYSTEM, "1")
        small.log(LogStage.SYSTEM, "2")
        small.log(LogStage.SYSTEM, "3")
        small.log(LogStage.SYSTEM, "4")
        small.log(LogStage.SYSTEM, "5")

        val entries = small.entries.value
        assertEquals(5, entries.size)
        assertEquals("1", entries.first().message)
        assertEquals("5", entries.last().message)
    }

    @Test
    fun clear_emptiesBuffer() {
        buffer.log(LogStage.SYSTEM, "x")
        buffer.log(LogStage.SYSTEM, "y")
        buffer.clear()

        assertTrue(buffer.entries.value.isEmpty())
    }

    @Test
    fun log_isConcurrentSafe_andNonBlocking() {
        val workers = 4
        val perWorker = 500
        val latch = CountDownLatch(workers)
        val pool = Executors.newFixedThreadPool(workers)

        repeat(workers) {
            pool.execute {
                try {
                    repeat(perWorker) { i ->
                        buffer.log(LogStage.SYSTEM, "w=$it i=$i")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("workers must finish without deadlock/exception", latch.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        val entries = buffer.entries.value
        assertEquals(AppLogBuffer.DEFAULT_CAPACITY, entries.size)
        // Newest entry must be retained (ring keeps tail).
        assertTrue(entries.last().message.contains("i=${perWorker - 1}"))
    }
}