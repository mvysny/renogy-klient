package utils

import java.io.PrintStream
import java.time.Instant
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration
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
