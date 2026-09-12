package datalogger

import clients.RenogyData
import clients.dummyRenogyData
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.expect
import kotlin.test.fail

class CompositeDataLoggerTest {
    private val logger1 = DummyDataLogger()
    private val logger2 = DummyDataLogger()
    private val composite = CompositeDataLogger().apply {
        dataLoggers.add(logger1)
        dataLoggers.add(logger2)
    }

    @Test fun `every call is fanned out to all loggers`() {
        composite.init()
        composite.append(dummyRenogyData, Instant.now())
        composite.deleteRecordsOlderThan(5)
        composite.close()

        for (logger in listOf(logger1, logger2)) {
            expect(true) { logger.inited }
            expect(listOf(dummyRenogyData)) { logger.data }
            expect(true) { logger.deleteRequested }
            expect(true) { logger.closed }
        }
    }

    @Test fun `close empties the logger list, so that a second close is a no-op`() {
        composite.close()
        expect(listOf()) { composite.dataLoggers }
        composite.close()
    }

    @Test fun `a logger failing to close doesn't prevent the others from closing`() {
        composite.dataLoggers.add(0, object : DataLogger {
            override fun init() {}
            override fun append(data: RenogyData, sampledAt: Instant) {}
            override fun deleteRecordsOlderThan(days: Int) {}
            override fun close() { throw java.io.IOException("simulated") }
        })
        composite.close()
        expect(true) { logger1.closed }
        expect(true) { logger2.closed }
    }

    @Test fun `an empty composite is happy to do nothing`() {
        CompositeDataLogger().use {
            it.init()
            it.append(dummyRenogyData, Instant.now())
            it.deleteRecordsOlderThan(5)
        }
    }

    @Test fun `a failing logger fails the whole composite`() {
        composite.dataLoggers.add(object : DataLogger {
            override fun init() { throw IllegalStateException("simulated") }
            override fun append(data: RenogyData, sampledAt: Instant) {}
            override fun deleteRecordsOlderThan(days: Int) {}
            override fun close() {}
        })
        try {
            composite.init()
            fail("Expected to fail")
        } catch (e: IllegalStateException) {
            expect("simulated") { e.message }
        }
    }
}
