package datalogger

import clients.RenogyData
import utils.Log
import utils.closeQuietly
import java.io.Closeable

/**
 * Logs [RenogyData] somewhere. All operations are blocking. Not thread safe.
 */
interface DataLogger : Closeable {
    /**
     * Initializes the logger; e.g. makes sure the CSV file exists and creates one with a header if it doesn't.
     */
    fun init()

    /**
     * Appends [data] to the logger. Blocks until the appending is done and finished. The function should automatically retry 5 times
     * on recoverable errors, such as connection resets, timeouts and such.
     */
    fun append(data: RenogyData)

    /**
     * Deletes all records older than given number of [days]. Blocks until the appending is done and finished.
     */
    fun deleteRecordsOlderThan(days: Int = 365)
}

/**
 * Aggregates multiple [DataLogger]s. Add them to [dataLoggers] before calling [init].
 */
class CompositeDataLogger : DataLogger {
    val dataLoggers = mutableListOf<DataLogger>()
    override fun init() {
        dataLoggers.forEach { it.init() }
    }

    override fun append(data: RenogyData) {
        dataLoggers.forEach { it.append(data) }
    }

    override fun deleteRecordsOlderThan(days: Int) {
        log.info("Deleting old records")
        dataLoggers.forEach { it.deleteRecordsOlderThan(days) }
        log.info("Successfully deleted old records")
    }

    override fun close() {
        dataLoggers.forEach { it.closeQuietly() }
        log.debug("Closed $dataLoggers")
        dataLoggers.clear()
    }

    override fun toString(): String = "CompositeDataLogger($dataLoggers)"

    companion object {
        private val log = Log<CompositeDataLogger>()
    }
}
