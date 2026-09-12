package clients

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.expect

class RenogyClientTest {
    @Test fun toJson() {
        expect("""{"systemInfo":{"maxVoltage":24,"ratedChargingCurrent":40,"ratedDischargingCurrent":40,"productType":"Controller","productModel":"RENOGY ROVER","softwareVersion":"v1.2.3","hardwareVersion":"v4.5.6","serialNumber":"1501FFFF"},"powerStatus":{"batterySOC":100,"batteryVoltage":25.6,"chargingCurrentToBattery":2.3,"batteryTemp":23,"controllerTemp":23,"loadVoltage":0.0,"loadCurrent":0.0,"loadPower":0,"solarPanelVoltage":60.2,"solarPanelCurrent":4.2,"solarPanelPower":252},"dailyStats":{"batteryMinVoltage":25.0,"batteryMaxVoltage":28.0,"maxChargingCurrent":10.0,"maxDischargingCurrent":10.0,"maxChargingPower":240,"maxDischargingPower":240,"chargingAh":100,"dischargingAh":100,"powerGenerationWh":0,"powerConsumptionWh":0},"historicalData":{"daysUp":20,"batteryOverDischargeCount":1,"batteryFullChargeCount":20,"totalChargingBatteryAH":2000,"totalDischargingBatteryAH":2000,"cumulativePowerGenerationWH":2000,"cumulativePowerConsumptionWH":2000},"status":{"streetLightOn":false,"streetLightBrightness":0,"chargingState":"MpptChargingMode","faults":["ControllerTemperatureTooHigh"]}}""") {
            dummyRenogyData.toJson(false)
        }
    }

    @Test fun ControllerFaults() {
        expect(setOf()) { ControllerFaults.fromModbus(0u) }
        expect(setOf(ControllerFaults.PhotovoltaicInputSideShortCircuit, ControllerFaults.BatteryOverDischarge)) {
            ControllerFaults.fromModbus(0x01010000u)
        }
    }

    @Test fun `every fault has its own bit`() {
        for (fault in ControllerFaults.entries) {
            expect(setOf(fault)) { ControllerFaults.fromModbus(1u.shl(fault.bit)) }
        }
        expect(ControllerFaults.entries.toSet()) {
            ControllerFaults.fromModbus(ControllerFaults.entries.fold(0u) { acc, f -> acc or 1u.shl(f.bit) })
        }
    }

    @Test fun `charging states are decoded by their modbus value`() {
        for (state in ChargingState.entries) {
            expect(state) { ChargingState.fromModbus(state.value) }
        }
        expect(null) { ChargingState.fromModbus(7u) }
        expect(null) { ChargingState.fromModbus(255u) }
    }

    @Test fun `the documented Renogy error codes are decoded`() {
        expect("0x1: Function code not supported") { RenogyException.fromCode(1).message }
        expect("0x5: Data check code sent by server is not correct") { RenogyException.fromCode(5).message }
        expect(5.toByte()) { RenogyException.fromCode(5).code }
        expect("0x6: Unknown") { RenogyException.fromCode(6).message }
        expect(null) { RenogyException("mangled response").code }
    }

    @Test fun `pretty-printed JSON round-trips`() {
        val json = dummyRenogyData.toJson()
        expect(true) { json.contains("\n") }
        expect(dummyRenogyData) { Json.decodeFromString<RenogyData>(json) }
    }
}
