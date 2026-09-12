package datalogger

import clients.RenogyData
import clients.dummyRenogyData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BackgroundTaskExecutor
import java.net.ConnectException
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.expect
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Blocks in [append] and [deleteRecordsOlderThan] for [blockFor] millis.
 */
private class SlowDataLogger(val blockFor: Long) : DataLogger {
    override fun init() {}
    override fun append(data: RenogyData, sampledAt: Instant) { Thread.sleep(blockFor) }
    override fun deleteRecordsOlderThan(days: Int) { Thread.sleep(blockFor) }
    override fun close() {}
}

class TimeoutDataLoggerTest {
    @BeforeEach
    fun setupExecutor() {
        Main.backgroundTasks = BackgroundTaskExecutor()
    }
    @AfterEach
    fun shutdownExecutor() {
        Main.backgroundTasks.kill()
    }
    @Test
    fun smoke() {
        TimeoutDataLogger(StdoutCSVDataLogger(false)).use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
            it.deleteRecordsOlderThan(5)
        }
    }

    @Test
    fun simpleCaseOnSuccess() {
        val dummy = DummyDataLogger()
        TimeoutDataLogger(dummy).use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
            it.deleteRecordsOlderThan(5)
        }
        expect(true) { dummy.inited }
        expect(listOf(dummyRenogyData)) { dummy.data }
        expect(true) { dummy.deleteRequested }
        expect(true) { dummy.closed }
    }

    @Test fun `append gives up on a wedged logger`() {
        TimeoutDataLogger(SlowDataLogger(60_000), timeoutAfter = 50.milliseconds).use {
            try {
                it.append(dummyRenogyData, Instant.now())
                fail("Expected to time out")
            } catch (e: TimeoutException) {
                // expected
            }
        }
    }

    @Test fun `deleteRecordsOlderThan gives up on a wedged logger`() {
        TimeoutDataLogger(SlowDataLogger(60_000), timeoutAfter = 50.milliseconds).use {
            try {
                it.deleteRecordsOlderThan(5)
                fail("Expected to time out")
            } catch (e: TimeoutException) {
                // expected
            }
        }
    }

    @Test fun `the delegate runs on a background thread, not on the caller's`() {
        val delegateThread = AtomicReference<Thread>()
        val logger = object : DataLogger {
            override fun init() {}
            override fun append(data: RenogyData, sampledAt: Instant) {
                delegateThread.set(Thread.currentThread())
            }
            override fun deleteRecordsOlderThan(days: Int) {}
            override fun close() {}
        }
        TimeoutDataLogger(logger).use { it.append(dummyRenogyData, Instant.now()) }
        expect(false) { delegateThread.get() == Thread.currentThread() }
    }

    /**
     * A canceled task must be retryable, otherwise a single wedged database would drop the sample
     * for good; see [RetryableDataLogger].
     */
    @Test fun `a cancellation is recoverable`() {
        TimeoutDataLogger(DummyDataLogger()).use {
            expect(true) { it.isRecoverable(CancellationException("canceled")) }
            expect(true) { it.isRecoverable(ConnectException("nope")) }
            expect(false) { it.isRecoverable(IllegalStateException("nope")) }
        }
    }

    @Test fun `retry on top of timeout recovers from a slow first attempt`() {
        val flaky = object : DataLogger {
            var calls = 0
            override fun init() {}
            override fun append(data: RenogyData, sampledAt: Instant) {
                if (calls++ == 0) Thread.sleep(60_000)
            }
            override fun deleteRecordsOlderThan(days: Int) {}
            override fun close() {}
        }
        RetryableDataLogger(
            TimeoutDataLogger(flaky, timeoutAfter = 50.milliseconds),
            times = 3,
            backoff = 1.milliseconds
        ).use {
            it.append(dummyRenogyData, Instant.now())
        }
        expect(2) { flaky.calls }
    }

    @Test fun `init and close are not wrapped, they go straight to the delegate`() {
        val dummy = DummyDataLogger()
        val logger = TimeoutDataLogger(dummy, timeoutAfter = 1.seconds)
        logger.init()
        logger.close()
        expect(true) { dummy.inited }
        expect(true) { dummy.closed }
    }
}
