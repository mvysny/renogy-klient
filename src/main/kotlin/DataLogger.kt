import clients.RenogyData
import org.slf4j.LoggerFactory
import utils.CSVWriter
import utils.closeQuietly
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.days

/**
 * Logs [RenogyData] somewhere.
 */
interface DataLogger : Closeable {
    /**
     * Initializes the logger; e.g. makes sure the CSV file exists and creates one with a header if it doesn't.
     */
    fun init()

    /**
     * Appends [data] to the logger.
     */
    fun append(data: RenogyData)

    /**
     * Deletes all records older than given number of [days].
     */
    fun deleteRecordsOlderThan(days: Int = 365)
}

private class CSVRenogyWriter(stream: PrintStream) {
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

/**
 * Aggregates multiple [DataLogger]s. Add them to [dataLoggers] before calling [init].
 */
class CompositeDataLogger : DataLogger {
    val dataLoggers = mutableListOf<DataLogger>()
    override fun init() {
        dataLoggers.forEach { it.init() }
    }

    override fun append(data: RenogyData) {
        dataLoggers.forEach { it.append(data) }
    }

    override fun deleteRecordsOlderThan(days: Int) {
        log.info("Deleting old records")
        dataLoggers.forEach { it.deleteRecordsOlderThan(days) }
        log.info("Successfully deleted old records")
    }

    override fun close() {
        dataLoggers.forEach { it.closeQuietly() }
        log.debug("Closed $dataLoggers")
        dataLoggers.clear()
    }

    override fun toString(): String = "CompositeDataLogger($dataLoggers)"

    companion object {
        private val log = LoggerFactory.getLogger(CompositeDataLogger::class.java)
    }
}

/**
 * Logs [RenogyData] to a CSV file.
 */
class CSVDataLogger(val file: File, val utc: Boolean) : DataLogger {
    private lateinit var out: PrintStream
    private lateinit var csv: CSVRenogyWriter
    override fun init() {
        if (!file.exists()) {
            out = PrintStream(file, Charsets.UTF_8)
            csv = CSVRenogyWriter(out)
            csv.writeHeader()
        } else {
            out = PrintStream(FileOutputStream(file, true), false, Charsets.UTF_8)
            csv = CSVRenogyWriter(out)
        }
    }

    override fun append(data: RenogyData) {
        csv.writeData(data, utc)
    }

    override fun deleteRecordsOlderThan(days: Int) {
        // it would take too much time to process a huge CSV file; also CSV is considered experimental, so don't bother
        log.info("Record cleanup not implemented for CSV")
    }

    override fun toString(): String = "CSVDataLogger($file, utc=$utc)"

    override fun close() {
        out.close()
    }

    companion object {
        private val log = LoggerFactory.getLogger(CSVDataLogger::class.java)
    }
}

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

/**
 * Logs data into PostgreSQL via the `psql` command-line client.
 * @param url the connection URL, e.g. `postgresql://user:pass@localhost:5432/postgres`
 * @param user if not present in [url], pass it here.
 * @param pass if not present in [url], pass it here.
 */
class PostgresDataLogger(val url: String, val user: String? = null, val pass: String? = null) : DataLogger {
    private lateinit var conn: Connection

    private fun sql(sql: String) {
        log.debug("Running: $sql")
        conn.createStatement().use {
            it.executeUpdate(sql)
        }
    }

    override fun init() {
        conn = DriverManager.getConnection(url, user, pass)
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

        sql("insert into log (${cols.joinToString(",")}) values (${values.joinToString(",")})")
    }

    override fun deleteRecordsOlderThan(days: Int) {
        log.info("Deleting old records")
        val deleteOlderThan = Instant.now().epochSecond - days.days.inWholeSeconds
        sql("delete from log where DateTime <= $deleteOlderThan")
        log.info("Successfully deleted old records")
    }

    override fun close() {}

    override fun toString(): String =
        "PostgresDataLogger($url)"

    companion object {
        private val log = LoggerFactory.getLogger(PostgresDataLogger::class.java)
    }
}
