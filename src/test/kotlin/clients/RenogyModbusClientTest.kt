package clients

import com.github.mvysny.dynatest.DynaTest
import utils.Buffer
import utils.addAll
import utils.toHex
import kotlin.test.expect
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class RenogyModbusClientTest : DynaTest({
    test("readRegister000ANormalResponse") {
        val buffer = Buffer()
        buffer.toReturn.addAll("010302181e324c")
        val client = RenogyModbusClient(buffer, 1.seconds)
        val response = client.readRegister(0x0A, 0x02)
        buffer.expectWrittenBytes("0103000a0001a408")
        expect("181e") { response.toHex() }
    }

    test("readRegister000AErrorResponse") {
        val buffer = Buffer()
        buffer.toReturn.addAll("018302c0f1")
        val client = RenogyModbusClient(buffer, 1.seconds)
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

    test("readRegister000CNormalResponse") {
        val buffer = Buffer()
        buffer.toReturn.addAll("010310202020204d5434383330202020202020ee98")
        val client = RenogyModbusClient(buffer, 1.seconds)
        val response = client.readRegister(0x0C, 16)
        buffer.expectWrittenBytes("0103000c0008840f")
        expect("202020204d5434383330202020202020") { response.toHex() }
    }

    test("ReadDailyStats") {
        val buffer = Buffer()
        // The 4th and 5th bytes 0070H indicate the current day's min. battery voltage: 0070H * 0.1 = 112 * 0.1 = 11.2V
        // The 6th and 7th bytes 0084H indicate the current day's max. battery voltage: 0084H * 0.1 = 132 * 0.1 = 13.2V
        // The 8th and 9th bytes 00D8H indicate the current day's max. charging current: 00D8H * 0.01 = 216 * 0.01 = 2.16V
        // then max discharge current: 0
        // then max charging power: 10
        // max discharging power: 0
        // 0608H are the current day's charging amp-hrs (decimal 1544AH);
        // 0810H are the current day's discharging amp-hrs (decimal 2064AH)
        buffer.toReturn.addAll("0103140070008400d80000000a00000608081000700084ebde")
        val client = RenogyModbusClient(buffer, 1.seconds)
        val dailyStats = client.getDailyStats()
        buffer.expectWrittenBytes("0103010b000ab5f3")
        expect(
            DailyStats(11.2f, 13.2f, 2.16f, 0f, 10.toUShort(), 0.toUShort(), 1544.toUShort(), 2064.toUShort(), 112.toUShort(), 132.toUShort())
        ) { dailyStats }
    }
})
