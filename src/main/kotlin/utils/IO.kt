package utils

import com.fazecast.jSerialComm.SerialPort
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class IOTimeoutException(msg: String) : IOException(msg)

/**
 * An IO pipe supporting most basic operations. Basically a thin wrap over [SerialPort]. Synchronous.
 */
interface IO : Closeable {
    /**
     * Read data from the serial port, blocking until [bytes] of data is read.
     *
     * The function waits at most [timeout]; if not all bytes are available then [IOTimeoutException] is thrown.
     * Pass 0 to wait infinitely (the default).
     * @throws IOTimeoutException on timeout
     */
    fun read(bytes: Int, timeout: Duration = Duration.ZERO): ByteArray

    /**
     * Write data to the serial port, blocking until all data is written.
     *
     * The function waits at most [timeout]; if not all bytes have been written then [IOTimeoutException] is thrown.
     * Pass 0 to wait infinitely (the default).
     * @throws IOTimeoutException on timeout
     */
    fun write(bytes: ByteArray, timeout: Duration = Duration.ZERO)
}

/**
 * Drains the pipe so that there are no stray bytes left. Blocks up until [timeout].
 */
fun IO.drain(timeout: Duration = 1.seconds) {
    try {
        while (true) {
            read(128, timeout)
        }
    } catch (e: IOTimeoutException) {
        // okay
    }
}

fun IO.drainQuietly(timeout: Duration = 1.seconds) {
    val log = Log(javaClass)
    log.debug("Draining $this")
    try {
        drain(timeout)
    } catch (e: IOException) {
        log.warn("Failed to drain $this", e)
    }
}

/**
 * Wraps [SerialPort] as [IO].
 * @property devName The serial device name, e.g. `/dev/ttyUSB0`.
 */
class SerialPortIO(val devName: File) : IO {
    private val serialPort = SerialPort.getCommPort(devName.absolutePath)

    fun configure() {
        check(serialPort.openPort()) { "Failed to open" }
        check(serialPort.setComPortParameters(9600, 8, 0, SerialPort.NO_PARITY)) { "Failed to set com port params" }
        // sets off CTS, RTS, XON and XOFF
        check(serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)) { "Failed to disable flow control" }
    }

    private fun configureTimeout(timeout: Duration) {
        require(!timeout.isNegative()) { "timeout: $timeout must not be negative" }
        val millis = timeout.inWholeMilliseconds.toInt()
        check(serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING, millis, millis)) {
            "Failed to set timeouts to $millis ms"
        }
    }

    private fun check(outcome: Boolean, errorMessageProvider: () -> String) {
        if (!outcome) {
            throw IOException("${serialPort}: ${errorMessageProvider()}")
        }
    }

    override fun read(bytes: Int, timeout: Duration): ByteArray {
        require(bytes >= 0) { "bytes: expected 0 or greater but got $bytes" }
        if (bytes == 0) return byteArrayOf()

        configureTimeout(timeout)
        val buf = ByteArray(bytes)
        val bytesRead = serialPort.readBytes(buf, bytes)
        check(bytesRead >= 0) { "readBytes() failed" }
        check(bytesRead in 0..bytes) { "Expected ${0..bytes} but got $bytesRead" }
        if (bytesRead < bytes) {
            throw IOTimeoutException("Timeout reading data; expected to read $bytes bytes but read $bytesRead bytes")
        }
        return buf
    }

    override fun write(bytes: ByteArray, timeout: Duration) {
        configureTimeout(timeout)
        val bytesWritten = serialPort.writeBytes(bytes, bytes.size)
        check(bytesWritten >= 0) { "writeBytes() failed" }
        check(bytesWritten in 0..bytes.size) { "Expected ${0..bytes.size} but got $bytesWritten" }
        if (bytesWritten < bytes.size) {
            throw IOTimeoutException("Timeout writing data; expected to write ${bytes.size} bytes but wrote $bytesWritten bytes")
        }
    }

    override fun close() {
        check(serialPort.closePort()) { "Failed to close" }
    }

    override fun toString(): String = "SerialPortIO($devName)"
}

/**
 * Closes this closeable. Calls [Closeable.close] but catches any exception and prints it to stderr.
 */
fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: Exception) {
        Log(javaClass).warn("Close failed: $e", e)
    }
}
