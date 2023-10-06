package datalogger

import clients.RenogyData
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import utils.Log
import java.time.Instant
import kotlin.time.Duration.Companion.days

/**
 * Logs data into PostgreSQL.
 * @param url the JDBC connection URL, e.g. `jdbc:postgresql://localhost:5432/postgres`
 * @param username the username
 * @param password the password
 */
class PostgresDataLogger(val url: String, val username: String?, val password: String?) :
    DataLogger {
    private lateinit var ds: HikariDataSource

    private fun sql(sql: String) {
        log.debug("Running: $sql")
        ds.connection.use { conn ->
            conn.createStatement().use {
                it.executeUpdate(sql)
            }
        }
    }

    override fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = this@PostgresDataLogger.username
            password = this@PostgresDataLogger.password
            maximumPoolSize = 1
        }
        ds = HikariDataSource(config)

        log.debug("Logging into $url")
        sql("CREATE TABLE IF NOT EXISTS log (" +
                "DateTime bigint primary key not null," +
                "BatterySOC smallint not null," +
                "BatteryVoltage real not null," +
                "ChargingCurrentToBattery real not null," +
                "BatteryTemp smallint not null," +
                "ControllerTemp smallint not null," +
                "SolarPanelVoltage real not null," +
                "SolarPanelCurrent real not null," +
                "SolarPanelPower smallint not null," +
                "Daily_BatteryMinVoltage real not null," +
                "Daily_BatteryMaxVoltage real not null," +
                "Daily_MaxChargingCurrent real not null," +
                "Daily_MaxChargingPower smallint not null," +
                "Daily_ChargingAmpHours smallint not null," +
                "Daily_PowerGeneration smallint not null," +
                "Stats_DaysUp int not null," +
                "Stats_BatteryOverDischargeCount smallint not null," +
                "Stats_BatteryFullChargeCount smallint not null," +
                "Stats_TotalChargingBatteryAH int not null," +
                "Stats_CumulativePowerGenerationWH int not null," +
                "ChargingState smallint," +
                "Faults text)")
    }

    override fun append(data: RenogyData) {
        val cols = mutableListOf<String>()
        val values = mutableListOf<String>()

        fun add(col: String, value: Any?) {
            if (value != null) {
                cols.add(col)
                values.add(
                    when (value) {
                        is Number, is UShort, is UInt, is UByte -> value.toString()
                        else -> "'$value'"
                    }
                )
            }
        }

        add("DateTime", Instant.now().epochSecond)
        add("BatterySOC", data.powerStatus.batterySOC)
        add("BatteryVoltage", data.powerStatus.batteryVoltage)
        add("ChargingCurrentToBattery", data.powerStatus.chargingCurrentToBattery)
        add("BatteryTemp", data.powerStatus.batteryTemp)
        add("ControllerTemp", data.powerStatus.controllerTemp)
        add("SolarPanelVoltage", data.powerStatus.solarPanelVoltage)
        add("SolarPanelCurrent", data.powerStatus.solarPanelCurrent)
        add("SolarPanelPower", data.powerStatus.solarPanelPower)
        add("Daily_BatteryMinVoltage", data.dailyStats.batteryMinVoltage)
        add("Daily_BatteryMaxVoltage", data.dailyStats.batteryMaxVoltage)
        add("Daily_MaxChargingCurrent", data.dailyStats.maxChargingCurrent)
        add("Daily_MaxChargingPower", data.dailyStats.maxChargingPower)
        add("Daily_ChargingAmpHours", data.dailyStats.chargingAh)
        add("Daily_PowerGeneration", data.dailyStats.powerGenerationWh)
        add("Stats_DaysUp", data.historicalData.daysUp)
        add("Stats_BatteryOverDischargeCount", data.historicalData.batteryOverDischargeCount)
        add("Stats_BatteryFullChargeCount", data.historicalData.batteryFullChargeCount)
        add("Stats_TotalChargingBatteryAH", data.historicalData.totalChargingBatteryAH)
        add("Stats_CumulativePowerGenerationWH", data.historicalData.cumulativePowerGenerationWH)
        add("ChargingState", data.status.chargingState?.value)
        add("Faults", data.status.faults.joinToString(",") { it.name } .ifBlank { null })

        sql("insert into log (${cols.joinToString(",")}) values (${values.joinToString(",")}) ON CONFLICT (DateTime) DO UPDATE SET ${cols.indices.joinToString { "${cols[it]} = ${values[it]}" }}")
    }

    override fun deleteRecordsOlderThan(days: Int) {
        val deleteOlderThan = Instant.now().epochSecond - days.days.inWholeSeconds
        sql("delete from log where DateTime <= $deleteOlderThan")
    }

    override fun close() {
        ds.close()
    }

    override fun toString(): String =
        "PostgresDataLogger($url)"

    companion object {
        private val log = Log<PostgresDataLogger>()
    }
}