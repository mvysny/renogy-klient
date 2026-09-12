package datalogger.influxdb

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.expect
import kotlin.test.fail

/**
 * Drives [InfluxDBTinyClient] against a throwaway HTTP server, so that the hand-rolled line
 * protocol and the error handling are pinned down without needing a real InfluxDB.
 */
class InfluxDBTinyClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: InfluxDBTinyClient

    /** The requests the server received, oldest first. */
    private val requests = mutableListOf<Request>()

    data class Request(val uri: String, val headers: Map<String, String>, val body: String)

    /** What the server answers with; defaults to an empty 204. */
    private var respondWith: Pair<Int, String> = 204 to ""

    @BeforeEach fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            requests.add(Request(exchange.requestURI.toString(), headers, body))
            val (code, response) = respondWith
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(code, if (bytes.isEmpty()) -1L else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        client = InfluxDBTinyClient("http://127.0.0.1:${server.address.port}", "my_org", "solar", "s3cr3t")
    }

    @AfterEach fun stopServer() {
        server.stop(0)
    }

    @Test fun `appendMeasurement posts the line protocol`() {
        client.appendMeasurement(
            "renogy",
            mapOf("BatterySOC" to 100.toUShort(), "BatteryVoltage" to 25.6f),
            Instant.ofEpochMilli(1_700_000_000_000)
        )
        expect(1) { requests.size }
        expect("\nrenogy BatterySOC=100i,BatteryVoltage=25.6 1700000000000000000\n") { requests[0].body }
    }

    @Test fun `integral types get the i suffix, floats and strings don't`() {
        client.appendMeasurement(
            "renogy",
            linkedMapOf(
                "ushort" to 1.toUShort(),
                "uint" to 2u,
                "ubyte" to 3.toUByte(),
                "ulong" to 4uL,
                "short" to 5.toShort(),
                "int" to 6,
                "byte" to 7.toByte(),
                "long" to 8L,
                "float" to 9.5f,
                "double" to 10.5,
                "string" to "eleven",
                "boolean" to true
            ),
            Instant.ofEpochMilli(0)
        )
        expect(
            "\nrenogy ushort=1i,uint=2i,ubyte=3i,ulong=4i,short=5i,int=6i,byte=7i,long=8i," +
                "float=9.5,double=10.5,string=\"eleven\",boolean=true 0\n"
        ) { requests[0].body }
    }

    @Test fun `null fields are dropped`() {
        client.appendMeasurement(
            "renogy",
            linkedMapOf("a" to 1, "Faults" to null, "b" to 2),
            Instant.ofEpochMilli(0)
        )
        expect("\nrenogy a=1i,b=2i 0\n") { requests[0].body }
    }

    @Test fun `the write request carries the org, bucket, precision and token`() {
        client.appendMeasurement("renogy", mapOf("a" to 1), Instant.ofEpochMilli(0))
        expect("/api/v2/write?org=my_org&bucket=solar&precision=ns") { requests[0].uri }
        expect("Token s3cr3t") { requests[0].headers["authorization"] }
        expect("text/plain; charset=utf-8") { requests[0].headers["content-type"] }
        expect("application/json") { requests[0].headers["accept"] }
    }

    @Test fun `delete posts the predicate as JSON`() {
        client.delete(InfluxDBDeleteRequest("2000-01-01T00:00:00Z", "2024-01-01T00:00:00Z", """_measurement="renogy""""))
        expect("/api/v2/delete?org=my_org&bucket=solar") { requests[0].uri }
        expect("application/json") { requests[0].headers["content-type"] }
        expect("""{"start":"2000-01-01T00:00:00Z","stop":"2024-01-01T00:00:00Z","predicate":"_measurement=\"renogy\""}""") {
            requests[0].body
        }
    }

    @Test fun `an omitted predicate stays out of the JSON`() {
        client.delete(InfluxDBDeleteRequest("2000-01-01T00:00:00Z", "2024-01-01T00:00:00Z"))
        expect("""{"start":"2000-01-01T00:00:00Z","stop":"2024-01-01T00:00:00Z"}""") { requests[0].body }
    }

    @Test fun `a failure response is parsed into an InfluxDBException`() {
        respondWith = 500 to """{"code":"internal error","message":"unexpected error writing points to database: timeout"}"""
        try {
            client.appendMeasurement("renogy", mapOf("a" to 1), Instant.ofEpochMilli(0))
            fail("Expected to fail")
        } catch (e: InfluxDBException) {
            expect(500) { e.failure.httpErrorCode }
            expect("internal error") { e.failure.error.code }
            expect("unexpected error writing points to database: timeout") { e.failure.error.message }
            expect("\nrenogy a=1i 0\n") { e.requestBody }
        }
    }

    @Test fun `a non-JSON failure response still produces an InfluxDBException`() {
        respondWith = 502 to "<html>502 Bad Gateway</html>"
        try {
            client.appendMeasurement("renogy", mapOf("a" to 1), Instant.ofEpochMilli(0))
            fail("Expected to fail")
        } catch (e: InfluxDBException) {
            expect(502) { e.failure.httpErrorCode }
            expect("unknown") { e.failure.error.code }
            expect("<html>502 Bad Gateway</html>") { e.failure.error.message }
        }
    }

    @Test fun `a 2xx other than 204 is a success`() {
        respondWith = 200 to "ok"
        client.appendMeasurement("renogy", mapOf("a" to 1), Instant.ofEpochMilli(0))
        expect(1) { requests.size }
    }

    @Test fun toStringHidesTheToken() {
        expect(false) { client.toString().contains("s3cr3t") }
    }
}
