package utils

import java.io.PrintStream

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
