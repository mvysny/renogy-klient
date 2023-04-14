package utils

import org.slf4j.LoggerFactory
import java.io.Closeable
import kotlin.random.Random
import kotlin.random.nextUInt

fun Random.nextFloat(from: Float, to: Float): Float =
    nextDouble(from.toDouble(), to.toDouble()).toFloat()

fun Random.nextUShort(from: UShort, to: UShort): UShort =
    nextUInt(from.toUInt(), to.toUInt()).toUShort()

/**
 * Closes this closeable. Calls [Closeable.close] but catches any exception and prints it to stderr.
 */
fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: Exception) {
        LoggerFactory.getLogger(javaClass).warn("Close failed: $e", e)
    }
}
