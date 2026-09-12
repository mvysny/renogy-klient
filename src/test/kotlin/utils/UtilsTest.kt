package utils

import java.io.IOException
import java.net.SocketException
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.expect
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class UtilsTest {
    @Test fun testToHex() {
        expect("00") { 0.toByte().toHex() }
        expect("ff") { 0xFF.toByte().toHex() }
        expect("0f") { 0x0F.toByte().toHex() }
        expect("a0") { 0xA0.toByte().toHex() }
        expect("0103140070008400d80000000a00000608081000700084ebde") {
            byteArrayOf(1, 3, 20, 0, 0x70, 0, 0x84.toByte(), 0, 0xd8.toByte(), 0, 0, 0, 10, 0, 0, 6, 8, 8, 0x10, 0, 0x70, 0, 0x84.toByte(), 0xeb.toByte(), 0xde.toByte()).toHex()
        }
    }

    @Test fun testFromHex() {
        expect("01031a0070008400d80000000a0000060808") {
            "01031a0070008400d80000000a0000060808".fromHex().toHex()
        }
    }

    @Test fun testToAsciiString() {
        expect("    MT4830      ") {
            byteArrayOf(0x20, 0x20, 0x20, 0x20, 0x4D, 0x54, 0x34, 0x38, 0x33, 0x30, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20).toAsciiString()
        }
        expect("") { byteArrayOf().toAsciiString() }
    }

    @Test fun `instants subtract to a Duration`() {
        val now = Instant.ofEpochMilli(1_700_000_000_000)
        expect(1500.milliseconds) { now.plusMillis(1500) - now }
        expect((-1500).milliseconds) { now - now.plusMillis(1500) }
        expect(Duration.ZERO) { now - now }
    }

    @Test fun `rootCause digs to the bottom of the chain`() {
        val bottom = IllegalStateException("bottom")
        expect(bottom) { RuntimeException("top", RuntimeException("middle", bottom)).rootCause }
        expect(bottom) { bottom.rootCause }
    }

    @Test fun `only a SocketException named 'Connection reset' is a connection reset`() {
        expect(true) { SocketException("Connection reset").isConnectionReset }
        expect(false) { SocketException("Broken pipe").isConnectionReset }
        expect(false) { SocketException().isConnectionReset }
        expect(false) { IOException("Connection reset").isConnectionReset }
    }

    @Test fun `random values stay within their bounds`() {
        repeat(100) {
            val f = Random.nextFloat(1f, 2f)
            expect(true, "$f") { f >= 1f && f < 2f }
            val s = Random.nextUShort(10u, 20u)
            expect(true, "$s") { s.toInt() in 10..19 }
        }
    }

    @Test fun `background threads are daemons and are named after their executor`() {
        val factory = daemonThreadFactory("bgtasks")
        val t1 = factory.newThread {}
        val t2 = factory.newThread {}
        expect("bgtasks-1") { t1.name }
        expect("bgtasks-2") { t2.name }
        expect(true) { t1.isDaemon }
    }

    @Test fun `scheduleAtTimeOfDay rolls over to tomorrow for a time already past`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("test"))
        try {
            val future = scheduler.scheduleAtTimeOfDay(LocalTime.now().minusHours(2)) {}
            val delay = future.getDelay(TimeUnit.MINUTES).toInt()
            expect(true, "$delay minutes") { delay in (21 * 60)..(22 * 60) }
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test fun `shutdownGracefully lets a short task finish`() {
        val executor = Executors.newSingleThreadExecutor(daemonThreadFactory("test"))
        val done = AtomicBoolean()
        executor.submit {
            Thread.sleep(50)
            done.set(true)
        }
        executor.shutdownGracefully()
        expect(true) { done.get() }
        expect(true) { executor.isShutdown }
    }
}
