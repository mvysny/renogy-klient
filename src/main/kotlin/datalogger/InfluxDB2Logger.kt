package datalogger

import clients.RenogyData
import datalogger.influxdb.InfluxDBTinyClient
import datalogger.influxdb.InfluxDBDeleteRequest
import utils.Log
import java.time.LocalDate

/**
 * Logs data into an InfluxDB2 database.
 * @property client influxdb2 client
 */
class InfluxDB2Logger(val client: InfluxDBTinyClient) :
    DataLogger {
    private val measurement = "renogy"

    override fun init() {
        log.debug("Logging into $client")
    }

    override fun append(data: RenogyData) {
        val fields = buildMap<String, Any?> {
            put("BatterySOC", data.powerStatus.batterySOC)
            put("BatteryVoltage", data.powerStatus.batteryVoltage)
            put("ChargingCurrentToBattery", data.powerStatus.chargingCurrentToBattery)
            put("BatteryTemp", data.powerStatus.batteryTemp)
            put("ControllerTemp", data.powerStatus.controllerTemp)
            put("SolarPanelVoltage", data.powerStatus.solarPanelVoltage)
            put("SolarPanelCurrent", data.powerStatus.solarPanelCurrent)
            put("SolarPanelPower", data.powerStatus.solarPanelPower)
            put("Daily_BatteryMinVoltage", data.dailyStats.batteryMinVoltage)
            put("Daily_BatteryMaxVoltage", data.dailyStats.batteryMaxVoltage)
            put("Daily_MaxChargingCurrent", data.dailyStats.maxChargingCurrent)
            put("Daily_MaxChargingPower", data.dailyStats.maxChargingPower)
            put("Daily_ChargingAmpHours", data.dailyStats.chargingAh)
            put("Daily_PowerGeneration", data.dailyStats.powerGenerationWh)
            put("Stats_DaysUp", data.historicalData.daysUp)
            put("Stats_BatteryOverDischargeCount", data.historicalData.batteryOverDischargeCount)
            put("Stats_BatteryFullChargeCount", data.historicalData.batteryFullChargeCount)
            put("Stats_TotalChargingBatteryAH", data.historicalData.totalChargingBatteryAH)
            put("Stats_CumulativePowerGenerationWH", data.historicalData.cumulativePowerGenerationWH)
            put("ChargingState", data.status.chargingState?.value)
            put("Faults", data.status.faults.joinToString(",") { it.name } .ifBlank { null })
        }

        client.appendMeasurement(measurement, fields)
    }

    override fun deleteRecordsOlderThan(days: Int) {
        val reqObject = InfluxDBDeleteRequest(
            "2000-01-01T00:00:00Z",
            "${LocalDate.now().minusDays(days.toLong())}T00:00:00Z",
            predicate = """_measurement="$measurement""""
        )
        client.delete(reqObject)
    }

    override fun close() {}

    override fun toString(): String =
        "InfluxDB2Logger($client)"

    companion object {
        private val log = Log<InfluxDB2Logger>()
    }
}
