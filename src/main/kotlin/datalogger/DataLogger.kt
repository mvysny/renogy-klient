package datalogger

import clients.RenogyData
import datalogger.influxdb.InfluxDBException
import utils.Log
import utils.closeQuietly
import utils.isConnectionReset
import utils.rootCause
import java.io.Closeable
import java.net.ConnectException
import java.net.http.HttpConnectTimeoutException
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Logs [RenogyData] somewhere. All operations are blocking. Not thread safe.
 */
interface DataLogger : Closeable {
    /**
     * Initializes the logger; e.g. makes sure the CSV file exists and creates one with a header if it doesn't.
     */
    fun init()

    /**
     * Appends [data] to the logger. Blocks until the appending is done and finished.
     */
    fun append(data: RenogyData, sampledAt: Instant)

    /**
     * Deletes all records older than given number of [days]. Blocks until the appending is done and finished.
     */
    fun deleteRecordsOlderThan(days: Int = 365)

    /**
     * If [append] or [deleteRecordsOlderThan] fails with an exception, the
     */
    fun isRecoverable(e: Throwable): Boolean = when {
        e is ConnectException -> true
        e is InfluxDBException && e.failure.httpErrorCode == 500 && e.failure.error.code == "internal error" && e.failure.error.message.contains("timeout") -> true
        e.rootCause.isConnectionReset -> true
        this is HttpConnectTimeoutException -> true
        else -> false
    }
}

/**
 * Aggregates multiple [DataLogger]s. Add them to [dataLoggers] before calling [init].
 */
class CompositeDataLogger : DataLogger {
    val dataLoggers = mutableListOf<DataLogger>()
    override fun init() {
        dataLoggers.forEach { it.init() }
    }

    override fun append(data: RenogyData, sampledAt: Instant) {
        dataLoggers.forEach { it.append(data, sampledAt) }
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

/**
 * If the [delegate] fails to append data or delete records, automatically retries
 * up to [times], with given [backoff] period.
 */
class RepeatingDataLogger(
    val delegate: DataLogger,
    val times: Int = 5,
    val backoff: Duration = 15.seconds
) : DataLogger by delegate {

    private fun retry(block: () -> Unit) {
        var retries = times
        while(true) {
            try {
                block()
                return // success
            } catch (e: Exception) {
                if (retries > 0 && isRecoverable(e)) {
                    log.warn("Failure occurred, retrying in $backoff", e)
                    Thread.sleep(backoff.inWholeMilliseconds)
                    retries--
                    log.info("Retrying")
                } else {
                    throw e
                }
            }
        }
    }

    override fun append(data: RenogyData, sampledAt: Instant) {
        retry {
            delegate.append(data, sampledAt)
        }
    }

    override fun deleteRecordsOlderThan(days: Int) {
        retry {
            deleteRecordsOlderThan(days)
        }
    }

    companion object {
        val log = Log<RepeatingDataLogger>()
    }
}
