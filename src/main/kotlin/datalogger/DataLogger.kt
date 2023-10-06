package datalogger

import clients.RenogyData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import utils.Log
import utils.closeQuietly
import java.io.Closeable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.LocalDate

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
        private val log = Log<CompositeDataLogger>()
    }
}

/**
 * Logs data into an InfluxDB2 database.
 * @property url the InfluxDB2 URL, e.g. `http://localhost:8086`
 * @property org the organization, e.g. `my_org`
 * @property bucket the bucket, e.g. `solar`
 * @property token the access token
 */
class InfluxDB2Logger(val url: String, val org: String, val bucket: String, val token: String) :
    DataLogger {
    private val measurement = "renogy"
    private val writeUri = URI("$url/api/v2/write?org=$org&bucket=$bucket&precision=ns")
    private val deleteUri = URI("$url/api/v2/delete?org=$org&bucket=$bucket")
    private lateinit var client: HttpClient

    override fun init() {
        client = HttpClient.newHttpClient()
        log.debug("Logging into $url")
    }

    override fun append(data: RenogyData) {
        // the line protocol: https://docs.influxdata.com/influxdb/cloud/reference/syntax/line-protocol/
        val fields = mutableListOf<String>()
        fun add(col: String, value: Any?) {
            if (value != null) {
                val formatted = when (value) {
                    is UShort, is UInt, is UByte, is ULong, is Short, is Int, is Byte, is Long -> "${value}i"
                    is String -> "\"$value\""
                    else -> value.toString()
                }
                fields.add("$col=$formatted")
            }
        }

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

        val line = "\n$measurement ${fields.joinToString(",")} ${System.currentTimeMillis() * 1_000_000}\n"

        HttpRequest.newBuilder(writeUri)
            .header("Authorization", "Token $token")
            .header("Content-Type", "text/plain; charset=utf-8")
            .header("Accept", "application/json")
            .execPost(line)
    }

    private fun HttpRequest.Builder.execPost(content: String) {
        log.debug("POSTing $content")
        val request = POST(BodyPublishers.ofString(content))
            .build()
        val response: HttpResponse<String> = client.send(request, BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "InfluxDB2 failed: ${response.statusCode()} ${response.body()}" }
    }

    override fun deleteRecordsOlderThan(days: Int) {
        val reqObject = InfluxDBDeleteRequest(
            "2000-01-01T00:00:00Z",
            "${LocalDate.now().minusDays(days.toLong())}T00:00:00Z",
            predicate = """_measurement="$measurement""""
        )
        HttpRequest.newBuilder(deleteUri)
            .header("Authorization", "Token $token")
            .header("Content-Type", "application/json")
            .execPost(Json.encodeToString(reqObject))
    }

    override fun close() {}

    override fun toString(): String =
        "InfluxDB2Logger($url, org=$org, bucket=$bucket)"

    companion object {
        private val log = Log<InfluxDB2Logger>()
    }
}

/**
 * See https://docs.influxdata.com/influxdb/v2.7/write-data/delete-data/
 * @property start e.g. `2020-03-01T00:00:00Z`
 * @property stop e.g. `2020-03-01T00:00:00Z`
 * @property predicate e.g. `_measurement="example-measurement" AND tag="foo"`
 */
@Serializable
data class InfluxDBDeleteRequest(
    val start: String,
    val stop: String,
    val predicate: String? = null
)
