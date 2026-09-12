package datalogger

import clients.RenogyData
import clients.dummyRenogyData
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.time.Instant
import kotlin.test.expect
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fails the first [failTimes] calls to [append] and [deleteRecordsOlderThan] with the exception
 * produced by [exception], then starts succeeding.
 */
private class FlakyDataLogger(val failTimes: Int, val exception: () -> Exception) : DataLogger {
    var appendCalls = 0
    var deleteCalls = 0
    override fun init() {}
    override fun append(data: RenogyData, sampledAt: Instant) {
        if (appendCalls++ < failTimes) throw exception()
    }
    override fun deleteRecordsOlderThan(days: Int) {
        if (deleteCalls++ < failTimes) throw exception()
    }
    override fun close() {}
}

class RetryableDataLoggerTest {
    @Test
    fun smoke() {
        RetryableDataLogger(StdoutCSVDataLogger(false)).use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
            it.deleteRecordsOlderThan(5)
        }
    }

    @Test
    fun simpleCaseOnSuccess() {
        val dummy = DummyDataLogger()
        RetryableDataLogger(dummy).use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
            it.deleteRecordsOlderThan(5)
        }
        expect(true) { dummy.inited }
        expect(listOf(dummyRenogyData)) { dummy.data }
        expect(true) { dummy.deleteRequested }
        expect(true) { dummy.closed }
    }

    @Test fun `a recoverable failure is retried until it succeeds`() {
        val flaky = FlakyDataLogger(3) { ConnectException("simulated") }
        RetryableDataLogger(flaky, times = 5, backoff = 1.milliseconds).use {
            it.append(dummyRenogyData, Instant.now())
            it.deleteRecordsOlderThan(5)
        }
        expect(4) { flaky.appendCalls }
        expect(4) { flaky.deleteCalls }
    }

    @Test fun `a non-recoverable failure is rethrown right away`() {
        val flaky = FlakyDataLogger(1) { IllegalStateException("simulated") }
        RetryableDataLogger(flaky, times = 5, backoff = 1.milliseconds).use {
            try {
                it.append(dummyRenogyData, Instant.now())
                fail("Expected to fail")
            } catch (e: IllegalStateException) {
                expect("simulated") { e.message }
            }
        }
        expect(1) { flaky.appendCalls }
    }

    @Test fun `the failure is rethrown once the retries run out`() {
        val flaky = FlakyDataLogger(Int.MAX_VALUE) { ConnectException("simulated") }
        RetryableDataLogger(flaky, times = 2, backoff = 1.milliseconds).use {
            try {
                it.append(dummyRenogyData, Instant.now())
                fail("Expected to fail")
            } catch (e: ConnectException) {
                expect("simulated") { e.message }
            }
        }
        expect(3) { flaky.appendCalls }   // the initial attempt plus 2 retries
    }

    @Test fun `a connection reset hiding in the cause chain is recoverable`() {
        val flaky = FlakyDataLogger(1) {
            RuntimeException("wrapped", java.net.SocketException("Connection reset"))
        }
        RetryableDataLogger(flaky, times = 5, backoff = 1.milliseconds).use {
            it.append(dummyRenogyData, Instant.now())
        }
        expect(2) { flaky.appendCalls }
    }

    @Test fun `init and close are not retried, they go straight to the delegate`() {
        val dummy = DummyDataLogger()
        val logger = RetryableDataLogger(dummy, times = 5, backoff = 1.milliseconds)
        logger.init()
        logger.close()
        expect(true) { dummy.inited }
        expect(true) { dummy.closed }
    }
}
