package utils

import kotlin.test.Test
import kotlin.test.expect

class UtilsTest {
    @Test fun testToHex() {
        expect("00") { 0.toByte().toHex() }
        expect("ff") { 0xFF.toByte().toHex() }
        expect("0f") { 0x0F.toByte().toHex() }
        expect("a0") { 0xA0.toByte().toHex() }
        expect("0103140070008400d80000000a00000608081000700084ebde") {
            byteArrayOf(1, 3, 20, 0, 0x70, 0, 0x84.toByte(), 0, 0xd8.toByte(), 0, 0, 0, 10, 0, 0, 6, 8, 8, 0x10, 0, 0x70, 0, 0x84.toByte(), 0xeb.toByte(), 0xde.toByte()).toHex()
        }
    }

    @Test fun testFromHex() {
        expect("01031a0070008400d80000000a0000060808") {
            "01031a0070008400d80000000a0000060808".fromHex().toHex()
        }
    }

    @Test fun testToAsciiString() {
        expect("    MT4830      ") {
            byteArrayOf(0x20, 0x20, 0x20, 0x20, 0x4D, 0x54, 0x34, 0x38, 0x33, 0x30, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20).toAsciiString()
        }
        expect("") { byteArrayOf().toAsciiString() }
    }
}
