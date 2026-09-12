package utils

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.expect

class CSVWriterTest {
    private val out = ByteArrayOutputStream()
    private val csv = CSVWriter(PrintStream(out, true, Charsets.UTF_8))

    private fun written(): String = out.toString(Charsets.UTF_8).replace("\r\n", "\n")

    @Test fun `numbers are bare, everything else is quoted`() {
        csv.writeLine(1, 2.5f, 3L, 4.toUShort(), 5.toUByte(), 6u, "seven", ChargingStateStub.Foo)
        expect("1,2.5,3,4,5,6,\"seven\",\"Foo\"\n") { written() }
    }

    @Test fun `a null becomes an empty cell`() {
        csv.writeLine(null, 1, null)
        expect(",1,\n") { written() }
    }

    @Test fun `an empty line is still a line`() {
        csv.writeLine()
        expect("\n") { written() }
    }

    @Test fun `every line lands on its own row`() {
        csv.writeLine("a")
        csv.writeLine("b")
        expect("\"a\"\n\"b\"\n") { written() }
    }

    private enum class ChargingStateStub { Foo }
}
