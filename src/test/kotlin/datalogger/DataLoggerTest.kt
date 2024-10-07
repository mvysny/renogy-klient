package datalogger

import clients.dummyRenogyData
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class DataLoggerTest {
    @Nested inner class CSVDataLoggerTests {
        @Test fun smoke() {
            CSVDataLogger(File.createTempFile("temp", "csv"), true).use {
                it.init()
                it.append(dummyRenogyData)
            }
        }
    }
    @Nested inner class StdoutCSVDataLoggerTests {
        @Test fun smoke() {
            StdoutCSVDataLogger(false).use {
                it.init()
                it.append(dummyRenogyData)
            }
        }
    }
}
