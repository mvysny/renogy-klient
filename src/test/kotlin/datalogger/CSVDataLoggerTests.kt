package datalogger

import clients.dummyRenogyData
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant

class CSVDataLoggerTests {
    @Test
    fun smoke() {
        CSVDataLogger(File.createTempFile("temp", "csv"), true).use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
        }
    }
}