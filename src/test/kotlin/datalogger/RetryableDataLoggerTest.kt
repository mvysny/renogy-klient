package datalogger

import clients.dummyRenogyData
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.expect

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
}
