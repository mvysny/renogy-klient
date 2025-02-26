package datalogger

import clients.dummyRenogyData
import org.junit.jupiter.api.Test
import java.time.Instant

class StdoutCSVDataLoggerTests {
    @Test
    fun smoke() {
        StdoutCSVDataLogger(false).use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
        }
    }
}