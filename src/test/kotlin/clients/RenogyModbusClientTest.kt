package clients

import org.junit.jupiter.api.Test
import utils.Buffer
import utils.CRC16Modbus
import utils.addAll
import utils.fromHex
import utils.toHex
import kotlin.test.expect
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Appends a well-formed "read register" response carrying [data] (hex), CRC included.
 */
private fun MutableList<Byte>.addResponse(data: String, deviceAddress: Byte = 1) {
    val payload = data.fromHex()
    val header = byteArrayOf(deviceAddress, 0x03, payload.size.toByte())
    val crc = CRC16Modbus()
    crc.update(header)
    crc.update(payload)
    addAll(header.toList())
    addAll(payload.toList())
    addAll(crc.crcBytes.toList())
}

class RenogyModbusClientTest {
    private val buffer = Buffer()
    private val client = RenogyModbusClient(buffer, 1.seconds, DeviceAddress.DEFAULT)

    @Test fun readRegister000ANormalResponse() {
        buffer.toReturn.addAll("010302181e324c")
        val response = client.readRegister(0x0A, 0x02)
        buffer.expectWrittenBytes("0103000a0001a408")
        expect("181e") { response.toHex() }
    }

    @Test fun readRegister000AErrorResponse() {
        buffer.toReturn.addAll("018302c0f1")
        try {
            client.readRegister(0x0A, 0x02)
            fail("Expected to fail with clients.RenogyException")
        } catch (e: RenogyException) {
            // okay
            expect("0x2: PDU start address is not correct or PDU start address + data length") {
                e.message
            }
        }
        buffer.expectWrittenBytes("0103000a0001a408")
    }

    @Test fun readRegister000CNormalResponse() {
        buffer.toReturn.addAll("010310202020204d5434383330202020202020ee98")
        val response = client.readRegister(0x0C, 16)
        buffer.expectWrittenBytes("0103000c0008840f")
        expect("202020204d5434383330202020202020") { response.toHex() }
    }

    @Test fun ReadDailyStats() {
        // The 4th and 5th bytes 0070H indicate the current day's min. battery voltage: 0070H * 0.1 = 112 * 0.1 = 11.2V
        // The 6th and 7th bytes 0084H indicate the current day's max. battery voltage: 0084H * 0.1 = 132 * 0.1 = 13.2V
        // The 8th and 9th bytes 00D8H indicate the current day's max. charging current: 00D8H * 0.01 = 216 * 0.01 = 2.16V
        // then max discharge current: 0
        // then max charging power: 10
        // max discharging power: 0
        // 0608H are the current day's charging amp-hrs (decimal 1544AH);
        // 0810H are the current day's discharging amp-hrs (decimal 2064AH)
        buffer.toReturn.addAll("0103140070008400d80000000a00000608081000700084ebde")
        val dailyStats = client.getDailyStats()
        buffer.expectWrittenBytes("0103010b000ab5f3")
        expect(
            DailyStats(11.2f, 13.2f, 2.16f, 0f, 10u, 0u, 1544u, 2064u, 112u, 132u)
        ) { dailyStats }
    }

    @Test fun `a response from a different device is rejected`() {
        buffer.toReturn.addResponse("181e", deviceAddress = 2)
        try {
            client.readRegister(0x0A, 0x02)
            fail("Expected to fail")
        } catch (e: RenogyException) {
            expect(true, e.message) { e.message!!.contains("Invalid response") }
        }
    }

    @Test fun `an unexpected function code is rejected`() {
        buffer.toReturn.addAll("010402181e324c")
        try {
            client.readRegister(0x0A, 0x02)
            fail("Expected to fail")
        } catch (e: RenogyException) {
            expect(true, e.message) { e.message!!.contains("Unexpected response code") }
        }
    }

    @Test fun `a bad checksum is rejected`() {
        // a valid frame with the two CRC bytes flipped
        buffer.toReturn.addAll("010302181e4c32")
        try {
            client.readRegister(0x0A, 0x02)
            fail("Expected to fail")
        } catch (e: RenogyException) {
            expect(true, e.message) { e.message!!.contains("Checksum mismatch") }
        }
    }

    @Test fun `a response of an unexpected length is rejected`() {
        buffer.toReturn.addResponse("181e2020")
        try {
            client.readRegister(0x0A, 0x02)
            fail("Expected to fail")
        } catch (e: IllegalArgumentException) {
            expect(true, e.message) { e.message!!.contains("expected to return 2 bytes but got 4") }
        }
    }

    @Test fun `the register address and length are validated`() {
        for (badAddress in listOf(-1, 0x1001)) {
            try {
                client.readRegister(badAddress, 2)
                fail("Expected $badAddress to be rejected")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        for (badLength in listOf(0, 0x100)) {
            try {
                client.readRegister(0x0A, badLength)
                fail("Expected $badLength to be rejected")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test fun `getSystemInfo decodes all four registers`() {
        buffer.toReturn.addResponse("18282800")                             // 0x0A: specs
        buffer.toReturn.addResponse("202020204d5434383330202020202020")     // 0x0C: model
        buffer.toReturn.addResponse("0001020300040506")                     // 0x14: sw/hw version
        buffer.toReturn.addResponse("1501ffff")                             // 0x18: serial number
        expect(
            SystemInfo(24, 40, 40, ProductType.Controller, "MT4830", "V1.2.3", "V4.5.6", "1501ffff")
        ) { client.getSystemInfo() }
    }

    @Test fun `an unknown product type decodes to null`() {
        buffer.toReturn.addResponse("18282807")
        buffer.toReturn.addResponse("202020204d5434383330202020202020")
        buffer.toReturn.addResponse("0001020300040506")
        buffer.toReturn.addResponse("1501ffff")
        expect(null) { client.getSystemInfo().productType }
    }

    @Test fun `getPowerStatus decodes the registers`() {
        buffer.toReturn.addResponse(
            "0064" +   // battery SOC: 100%
            "0100" +   // battery voltage: 256 * 0.1 = 25.6V
            "00e6" +   // charging current: 230 * 0.01 = 2.3A
            "1718" +   // controller temp 23C, battery temp 24C
            "0000" +   // load voltage
            "0000" +   // load current
            "0000" +   // load power
            "025a" +   // solar panel voltage: 602 * 0.1 = 60.2V
            "01a4" +   // solar panel current: 420 * 0.01 = 4.2A
            "00fc"     // solar panel power: 252W
        )
        expect(
            PowerStatus(100u, 25.6f, 2.3f, 24, 23, 0f, 0f, 0u, 60.2f, 4.2f, 252u)
        ) { client.getPowerStatus() }
    }

    @Test fun `getHistoricalData decodes the registers`() {
        buffer.toReturn.addResponse(
            "0014" +       // days up: 20
            "0001" +       // battery over-discharge count: 1
            "0014" +       // battery full charge count: 20
            "000007d0" +   // total charging AH: 2000
            "000007d0" +   // total discharging AH: 2000
            "000007d0" +   // cumulative power generation: 2000 WH
            "000007d0"     // cumulative power consumption: 2000 WH
        )
        expect(
            HistoricalData(20u, 1u, 20u, 2000u, 2000u, 2000u, 2000u)
        ) { client.getHistoricalData() }
    }

    @Test fun `getStatus decodes the street light, the charging state and the faults`() {
        buffer.toReturn.addResponse("e2" + "02" + "01010000")
        expect(
            RenogyStatus(
                streetLightOn = true,
                streetLightBrightness = 0x62u,
                chargingState = ChargingState.MpptChargingMode,
                faults = setOf(
                    ControllerFaults.PhotovoltaicInputSideShortCircuit,
                    ControllerFaults.BatteryOverDischarge
                )
            )
        ) { client.getStatus() }
    }

    @Test fun `getStatus reports a dark, fault-free controller`() {
        buffer.toReturn.addResponse("0000" + "00000000")
        expect(
            RenogyStatus(false, 0u, ChargingState.ChargingDeactivated, setOf())
        ) { client.getStatus() }
    }

    @Test fun `an unknown charging state decodes to null`() {
        buffer.toReturn.addResponse("007f" + "00000000")
        expect(null) { client.getStatus().chargingState }
    }

    @Test fun `getAllData reuses the cached system info`() {
        buffer.toReturn.addResponse("0064010000e617180000000000000000000000fc")  // power status
        buffer.toReturn.addResponse("0070008400d80000000a00000608081000700084")  // daily stats
        buffer.toReturn.addResponse("00140001001400000000000000000000000000000000")  // historical
        buffer.toReturn.addResponse("000200000000")  // status
        val data = client.getAllData(dummySystemInfo)
        expect(dummySystemInfo) { data.systemInfo }
        expect(100u.toUShort()) { data.powerStatus.batterySOC }
    }

    @Test fun `close doesn't close the io`() {
        client.close()
        buffer.toReturn.addAll("010302181e324c")
        expect("181e") { client.readRegister(0x0A, 0x02).toHex() }
    }
}

class DeviceAddressTest {
    @Test fun `the default is 1`() {
        expect(1.toUByte()) { DeviceAddress.DEFAULT.address }
    }

    @Test fun `addresses above the broadcast range are rejected`() {
        for (bad in 0xf8..0xff) {
            try {
                DeviceAddress(bad.toUByte())
                fail("Expected $bad to be rejected")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test fun `the whole documented range is accepted`() {
        for (good in 0..0xf7) {
            expect(good.toUByte()) { DeviceAddress(good.toUByte()).address }
        }
    }

    /**
     * A high address must reach the wire as its raw byte, not as a sign-extended or clamped one.
     */
    @Test fun `an address above 127 is framed correctly`() {
        val buffer = Buffer()
        buffer.toReturn.addResponse("181e", deviceAddress = 0xf7.toByte())
        val client = RenogyModbusClient(buffer, 1.seconds, DeviceAddress(0xf7u))
        expect("181e") { client.readRegister(0x0A, 0x02).toHex() }
        expect(true, buffer.writtenBytes.toByteArray().toHex()) {
            buffer.writtenBytes.toByteArray().toHex().startsWith("f703")
        }
    }
}
