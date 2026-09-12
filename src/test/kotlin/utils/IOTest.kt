package utils

import java.io.IOException
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An [IO] handing out [data] in chunks of at most the requested size, the way a serial port does;
 * once exhausted it times out.
 */
private class ChunkedIO(private val data: ByteArray) : IO {
    var readSoFar = 0
        private set
    var closed = false
        private set

    override fun read(bytes: Int, timeout: Duration): ByteArray {
        if (readSoFar >= data.size) throw IOTimeoutException("nothing left to read")
        val count = minOf(bytes, data.size - readSoFar)
        val result = data.copyOfRange(readSoFar, readSoFar + count)
        readSoFar += count
        return result
    }

    override fun write(bytes: ByteArray, timeout: Duration) {}
    override fun close() { closed = true }
}

class IOTest {
    @Test fun `drain consumes everything still in the pipe`() {
        val io = ChunkedIO(ByteArray(300))
        io.drain(10.milliseconds)
        expect(300) { io.readSoFar }
    }

    @Test fun `draining an empty pipe is a no-op`() {
        val io = ChunkedIO(ByteArray(0))
        io.drain(10.milliseconds)
        expect(0) { io.readSoFar }
    }

    @Test fun `drainQuietly swallows an IO failure`() {
        val io = object : IO {
            override fun read(bytes: Int, timeout: Duration): ByteArray = throw IOException("simulated")
            override fun write(bytes: ByteArray, timeout: Duration) {}
            override fun close() {}
        }
        io.drainQuietly(10.milliseconds)
    }

    @Test fun `drainQuietly does not swallow a non-IO failure`() {
        val io = object : IO {
            override fun read(bytes: Int, timeout: Duration): ByteArray = throw IllegalStateException("simulated")
            override fun write(bytes: ByteArray, timeout: Duration) {}
            override fun close() {}
        }
        try {
            io.drainQuietly(10.milliseconds)
            fail("Expected to fail")
        } catch (e: IllegalStateException) {
            expect("simulated") { e.message }
        }
    }

    @Test fun `closeQuietly closes`() {
        val io = ChunkedIO(ByteArray(0))
        io.closeQuietly()
        expect(true) { io.closed }
    }

    @Test fun `closeQuietly swallows a failing close`() {
        val closeable = java.io.Closeable { throw IOException("simulated") }
        closeable.closeQuietly()
    }
}
