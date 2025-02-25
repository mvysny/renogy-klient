package datalogger.influxdb

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import utils.Log
import utils.rootCause
import java.net.ConnectException
import java.net.SocketException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    private fun retryAsync(times: Int = 5, backoff: Duration = 15.seconds, block: () -> Unit) {
        var retries = times
        while(true) {
            try {
                block()
                return // success
            } catch (e: Exception) {
                if (e.shouldRetry && retries > 0) {
                    log.warn("Failure occurred, retrying in $backoff: $e")
                    Thread.sleep(backoff.inWholeMilliseconds)
                    retries--
                    log.info("Retrying")
                } else {
                    throw e
                }
            }
        }
    }

    private val Exception.shouldRetry: Boolean get() = when {
        this is ConnectException -> true
        this is InfluxDBException && failure.httpErrorCode == 500 && failure.error.code == "internal error" && failure.error.message.contains("timeout") -> true
        this.rootCause.isConnectionReset -> true
        this is HttpConnectTimeoutException -> true
        else -> false
    }
    private val Throwable.isConnectionReset: Boolean get() = this is SocketException && message == "Connection reset"

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
        buildRequest(deleteUri, "application/json")
            .execPost(Json.encodeToString(request))
    }

    fun appendMeasurement(measurement: String, data: Map<String, Any?>) {
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

        val line = "\n$measurement $fields ${System.currentTimeMillis() * 1_000_000}\n"

        val req = buildRequest(writeUri, "text/plain; charset=utf-8")
        retryAsync {
            req.execPost(line)
        }
    }

    companion object {
        private val log = Log<InfluxDBTinyClient>()
    }
}
