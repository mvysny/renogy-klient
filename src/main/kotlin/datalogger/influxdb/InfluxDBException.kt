package datalogger.influxdb

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import utils.Log
import java.lang.RuntimeException
import java.net.http.HttpResponse

/**
 * @property httpErrorCode e.g. 500 for internal server error
 */
data class InfluxDBFailure(
    val httpErrorCode: Int,
    val error: InfluxDBError
) {
    companion object {
        private val log = Log<InfluxDBFailure>()

        fun parse(response: HttpResponse<String>): InfluxDBFailure {
            val statusCode = response.statusCode()
            val json = response.body()
            val err = try {
                Json.decodeFromString<InfluxDBError>(json)
            } catch (e: SerializationException) {
                log.debug("Failed to deserialize ${json}: $e", e)
                InfluxDBError("unknown", json)
            }
            return InfluxDBFailure(statusCode, err)
        }
    }
}

/**
 * @property code for example "internal error"
 * @property message unexpected error writing points to database: timeout
 */
@Serializable
data class InfluxDBError(
    val code: String,
    val message: String,
)

class InfluxDBException(
    val requestBody: String,
    val failure: InfluxDBFailure
) : RuntimeException("Failed to POST $requestBody: $failure")
