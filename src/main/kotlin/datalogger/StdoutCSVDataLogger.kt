package datalogger

import clients.RenogyData
import utils.CSVWriter
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Logs [RenogyData] as a CSV file to stdout.
 */
class StdoutCSVDataLogger(val utc: Boolean) : DataLogger {
    private val csv = CSVRenogyWriter(System.out)
    override fun init() {
        csv.writeHeader()
    }

    override fun append(data: RenogyData) {
        csv.writeData(data, utc)
    }

    override fun deleteRecordsOlderThan(days: Int) {
    }

    override fun toString(): String {
        return "StdoutCSVDataLogger(utc=$utc)"
    }

    override fun close() {}
}

class CSVRenogyWriter(stream: PrintStream) {
    private val csv = CSVWriter(stream)
    fun writeHeader() {
        csv.writeLine(
            "DateTime",
            "BatterySOC",
            "BatteryVoltage",
            "ChargingCurrentToBattery",
            "BatteryTemp",
            "ControllerTemp",
            "SolarPanelVoltage",
            "SolarPanelCurrent",
            "SolarPanelPower",
            "Daily.BatteryMinVoltage",
            "Daily.BatteryMaxVoltage",
            "Daily.MaxChargingCurrent",
            "Daily.MaxChargingPower",
            "Daily.ChargingAmpHours",
            "Daily.PowerGeneration",
            "Stats.DaysUp",
            "Stats.BatteryOverDischargeCount",
            "Stats.BatteryFullChargeCount",
            "Stats.TotalChargingBatteryAH",
            "Stats.CumulativePowerGenerationWH",
            "ChargingState",
            "Faults"
        )
    }

    fun writeData(data: RenogyData, utc: Boolean) {
        csv.writeLine(
            if (utc) ZonedDateTime.now(ZoneOffset.UTC).toString() else LocalDateTime.now().toString(),
            data.powerStatus.batterySOC,
            data.powerStatus.batteryVoltage,
            data.powerStatus.chargingCurrentToBattery,
            data.powerStatus.batteryTemp,
            data.powerStatus.controllerTemp,
            data.powerStatus.solarPanelVoltage,
            data.powerStatus.solarPanelCurrent,
            data.powerStatus.solarPanelPower,
            data.dailyStats.batteryMinVoltage,
            data.dailyStats.batteryMaxVoltage,
            data.dailyStats.maxChargingCurrent,
            data.dailyStats.maxChargingPower,
            data.dailyStats.chargingAh,
            data.dailyStats.powerGenerationWh,
            data.historicalData.daysUp,
            data.historicalData.batteryOverDischargeCount,
            data.historicalData.batteryFullChargeCount,
            data.historicalData.totalChargingBatteryAH,
            data.historicalData.cumulativePowerGenerationWH,
            data.status.chargingState?.name,
            data.status.faults.joinToString(",") { it.name }
        )
    }
}
