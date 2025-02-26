package datalogger.influxdb

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import utils.Log
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/**
 * @property url the InfluxDB2 URL, e.g. `http://localhost:8086`
 * @property org the organization, e.g. `my_org`
 * @property bucket the bucket, e.g. `solar`
 * @property token the access token
 */
class InfluxDBTinyClient(
    val url: String, val org: String, val bucket: String, val token: String
) {
    private val writeUri =
        URI("$url/api/v2/write?org=$org&bucket=$bucket&precision=ns")
    private val deleteUri = URI("$url/api/v2/delete?org=$org&bucket=$bucket")
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .build()

    override fun toString(): String =
        "InfluxDBClient(url='$url', org='$org', bucket='$bucket')"

    private fun HttpRequest.Builder.execPost(content: String) {
        log.debug("POSTing $content")
        val request = POST(HttpRequest.BodyPublishers.ofString(content))
            .build()
        val response: HttpResponse<String> = client.send(request,
            HttpResponse.BodyHandlers.ofString()
        )
        if (response.statusCode() !in 200..299) {
            val failure = InfluxDBFailure.parse(response)
            throw InfluxDBException(content, failure)
        }
    }

    /**
     * @param contentType `application/json` or `text/plain; charset=utf-8`
     */
    private fun buildRequest(uri: URI, contentType: String): HttpRequest.Builder =
        HttpRequest.newBuilder(uri)
            .header("Authorization", "Token $token")
            .header("Content-Type", contentType)
            .header("Accept", "application/json")

    /**
     * See https://docs.influxdata.com/influxdb/v2.7/write-data/delete-data/
     * @param request the delete request
     */
    fun delete(request: InfluxDBDeleteRequest) {
        log.debug("POSTing $request")
        buildRequest(deleteUri, "application/json")
            .execPost(Json.encodeToString(request))
        log.debug("Request posted successfully")
    }

    /**
     * Appends a new measurement named [measurement].
     * @param sampledAt when the measurement was taken.
     */
    fun appendMeasurement(measurement: String, data: Map<String, Any?>, sampledAt: Instant) {
        // the line protocol: https://docs.influxdata.com/influxdb/cloud/reference/syntax/line-protocol/
        fun format(value: Any): String = when (value) {
            is UShort, is UInt, is UByte, is ULong, is Short, is Int, is Byte, is Long -> "${value}i"
            is String -> "\"$value\""
            else -> value.toString()
        }

        // in the format: col=value,col2=value2,...
        val fields: String = data.entries
            .filter { it.value != null }
            .joinToString(",") { "${it.key}=${format(it.value!!)}" }

        val line = "\n$measurement $fields ${sampledAt.toEpochMilli() * 1_000_000}\n"

        val req = buildRequest(writeUri, "text/plain; charset=utf-8")
        log.debug("POSTing: $line")
        req.execPost(line)
        log.debug("Data posted successfully")
    }

    companion object {
        private val log = Log<InfluxDBTinyClient>()
    }
}
