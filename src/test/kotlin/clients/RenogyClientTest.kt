package clients

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
}
