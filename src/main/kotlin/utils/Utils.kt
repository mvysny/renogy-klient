package utils

import java.io.PrintStream
import java.net.SocketException
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

fun Random.nextFloat(from: Float, to: Float): Float =
    nextDouble(from.toDouble(), to.toDouble()).toFloat()

fun Random.nextUShort(from: UShort, to: UShort): UShort =
    nextUInt(from.toUInt(), to.toUInt()).toUShort()

/**
 * Formats this byte array as a string of hex, e.g. "02ff35"
 */
fun ByteArray.toHex(): String = joinToString(separator = "") { it.toHex() }

/**
 * Formats this byte as a 2-character hex value, e.g. "03" or "fe".
 */
fun Byte.toHex(): String = toUByte().toInt().toString(16).padStart(2, '0')

/**
 * Converts every byte in the array to char and concatenates it as a string. Pure ASCII is used, no UTF-8 conversion is done.
 */
fun ByteArray.toAsciiString() = toString(Charsets.US_ASCII)

operator fun Instant.minus(other: Instant): Duration = (this.toEpochMilli() - other.toEpochMilli()).milliseconds

/**
 * Simple CSV writer, writing CSV lines to given [out].
 */
class CSVWriter(private val out: PrintStream) {
    fun writeLine(vararg line: Any?) {
        val row = line.joinToString(",") {
            when (it) {
                null -> ""
                is Number, is UShort, is UInt, is UByte -> it.toString()
                else -> "\"$it\""
            }
        }
        out.println(row)
    }
}

fun ScheduledExecutorService.scheduleAtTimeOfDay(timeOfDay: LocalTime, command: () -> Unit): ScheduledFuture<*> {
    var millisToNextExecution = ChronoUnit.MILLIS.between(LocalTime.now(), timeOfDay)
    if (millisToNextExecution < 0) {
        millisToNextExecution += 1.days.inWholeMilliseconds
    }
    return scheduleAtFixedRate(command, millisToNextExecution, 1.days.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}

fun ScheduledExecutorService.scheduleAtFixedRate(rate: Duration, command: () -> Unit): ScheduledFuture<*> =
    scheduleAtFixedRate(command, 0L, rate.inWholeMilliseconds, TimeUnit.MILLISECONDS)

fun <T> Future<T>.get(timeout: Duration): T? = get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

/**
 * Returns the root cause of this exception - goes through the [Throwable.cause] chain until the last one.
 */
val Throwable.rootCause: Throwable get() {
    var self = this
    while(true) {
        val cause = self.cause ?: return self
        self = cause
    }
}

/**
 * Returns true if this exception is a TCP/IP "Connection reset" exception.
 */
val Throwable.isConnectionReset: Boolean get() = this is SocketException && message == "Connection reset"

fun daemonThreadFactory(executorName: String): ThreadFactory {
    val threadId = AtomicInteger()
    return ThreadFactory { r ->
        val thread = Thread(r)
        thread.name = "$executorName-${threadId.incrementAndGet()}"
        thread.isDaemon = true
        thread
    }
}
