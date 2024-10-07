package datalogger

import clients.dummyRenogyData
import org.junit.jupiter.api.Test

class StdoutCSVDataLoggerTests {
    @Test
    fun smoke() {
        StdoutCSVDataLogger(false).use {
            it.init()
            it.append(dummyRenogyData)
        }
    }
}