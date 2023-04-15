package utils

import kotlin.test.expect
import kotlin.time.Duration

/**
 * A memory buffer, stores all written bytes to [writtenBytes]; [readFully] will offer
 * bytes from [toReturn].
 * @param maxIOBytes max number of bytes to accept during [write] and offer during [read].
 */
class Buffer() : IO {
    /**
     * Holds bytes written via [write]
     */
    val writtenBytes = mutableListOf<Byte>()    // not very effective, but this is just for testing purposes

    /**
     * Will be returned via [read].
     */
    val toReturn = mutableListOf<Byte>()     // not very effective, but this is just for testing purposes

    /**
     * The current read pointer; next call to [read] will return byte from [toReturn]
     * at this index. Automatically increased as [read] is called further.
     */
    var readPointer = 0

    override fun write(bytes: ByteArray, timeout: Duration) {
        writtenBytes.addAll(bytes.toList())
    }

    override fun read(bytes: Int, timeout: Duration): ByteArray {
        if (readPointer + bytes > toReturn.size) throw IOTimeoutException("readPointer=$readPointer toReturn=${toReturn.size} bytes=$bytes")
        val result = ByteArray(bytes) { toReturn[readPointer + it] }
        readPointer += bytes
        return result
    }

    override fun close() {}

    override fun toString(): String =
        "Buffer(written=${writtenBytes.toByteArray().toHex()}, toReturn=${toReturn.toByteArray().toHex()}, readPointer=$readPointer)"

    fun expectWrittenBytes(hexBytes: String) {
        expect(hexBytes) { writtenBytes.toByteArray().toHex() }
    }
}

fun MutableList<Byte>.addAll(hex: String) {
    addAll(hex.fromHex().toList())
}

/**
 * Converts a string such as "03fe" from hex to byte array.
 */
fun String.fromHex(): ByteArray {
    require(length % 2 == 0) { "$this must be an even-length string" }
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toUByte(16).toByte() }
}
