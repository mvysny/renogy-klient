package clients

import Log
import utils.*
import java.io.File
import kotlin.time.Duration

/**
 * Keeps a comm pipe open during the whole duration of this client.
 * Workarounds [Issue 10](https://github.com/mvysny/solar-controller-client/issues/10) by closing/reopening
 * the pipe on timeout.
 *
 * The client will then re-throw the exception and will not reattempt to re-read new data. The reason is
 * that the main loop will call us again anyways.
 * @property file The serial device name, e.g. `/dev/ttyUSB0`. [SerialPortIO] is constructed out of it.
 * @property timeout the read+write timeout.
 */
class RetryOnTimeoutClient(val file: File, val timeout: Duration) : RenogyClient {
    /**
     * Currently used [IO]. Closed on timeout.
     */
    private var io: SerialPortIO? = null

    /**
     * Gets the current [IO], opening a new [SerialPortIO] if there's no current one.
     */
    private fun getIO(): SerialPortIO {
        if (io == null) {
            io = SerialPortIO(file).apply {
                configure()
                drainQuietly()
            }
        }
        return io!!
    }

    private fun <T> runAndMitigateExceptions(block: (IO) -> T) : T {
        try {
            return block(getIO())
        } catch (e: RenogyException) {
            // perhaps there's some leftover data in the serial port? Drain.
            log.warn("Caught $e, draining $io")
            io?.drainQuietly()
            throw e
        } catch (t: IOTimeoutException) {
            // the serial port would simply endlessly fail with TimeoutException.
            // Try to remedy the situation by closing the IO and opening it again on next request.
            log.warn("Caught $t, closing $io")
            io?.closeQuietly()
            io = null
            throw t
        }
    }

    override fun getSystemInfo(): SystemInfo =
        runAndMitigateExceptions { io -> RenogyModbusClient(io, timeout).getSystemInfo() }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData =
        runAndMitigateExceptions { io -> RenogyModbusClient(io, timeout).getAllData(cachedSystemInfo) }

    override fun close() {
        io?.close()
        io = null
    }

    override fun toString(): String = "RetryOnTimeoutClient($file)"

    companion object {
        private val log = Log<RetryOnTimeoutClient>()
    }
}
