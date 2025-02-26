package datalogger

import clients.dummyRenogyData
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utils.BackgroundTaskExecutor
import java.time.Instant
import kotlin.test.expect

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
}
