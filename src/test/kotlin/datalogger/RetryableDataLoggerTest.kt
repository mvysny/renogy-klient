package datalogger

import clients.dummyRenogyData
import org.junit.jupiter.api.Test
import java.time.Instant

class RetryableDataLoggerTest {
    @Test
    fun smoke() {
        RetryableDataLogger(StdoutCSVDataLogger(false)).use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
            it.deleteRecordsOlderThan(5)
        }
    }
}
