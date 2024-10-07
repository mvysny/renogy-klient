package datalogger

import clients.dummyRenogyData
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.query.FluxTable
import org.testcontainers.containers.InfluxDBContainer
import org.testcontainers.utility.DockerImageName
import java.time.OffsetDateTime
import kotlin.test.expect
import datalogger.influxdb.InfluxDBTinyClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory

class InfluxDBDataLoggerTests {
    companion object {
        private val token = "tgPiZMSv30US40AX_v9zV-dTexHeJ1u4zNCQYEGNW13DNbLiCUFxpVLPZZtX7C0f8UfN84oS3jRxaKWlAICKmA=="
        private lateinit var container: InfluxDBContainer<*>
        private lateinit var client: InfluxDBClient
        private lateinit var fluxQuery: String
        @BeforeAll @JvmStatic fun startInflux() {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker must be available")

            container = InfluxDBContainer(DockerImageName.parse("influxdb:2.7.1"))
            container.withAdminToken(token)
            container.start()

            client = InfluxDBClientFactory.create(container.url, token.toCharArray(), container.organization, container.bucket)
            fluxQuery = ("""from(bucket: "${container.bucket}")
 |> range(start: -1d, stop: 1d)
 |> filter(fn: (r) => (r["_measurement"] == "renogy" and r["_field"] == "BatterySOC"))""")
        }

        @AfterAll @JvmStatic fun stopInflux() {
            client.close()
            container.stop()
        }
    }

    @BeforeEach fun deleteData() {
        client.deleteApi.delete(OffsetDateTime.now().minusYears(1), OffsetDateTime.now().plusYears(1), "", container.bucket, container.organization)
    }

    @Test fun smoke() {
        InfluxDB2Logger(InfluxDBTinyClient(container.url, container.organization, container.bucket, token)).use {
            it.init()
            it.append(dummyRenogyData)
            val result: MutableList<FluxTable> = client.queryApi.query(fluxQuery)
            expect(1) { result.size }
            expect(100L) { result[0].records[0].value }
            it.deleteRecordsOlderThan()
        }
    }
    @Test fun upsert() {
        InfluxDB2Logger(InfluxDBTinyClient(container.url, container.organization, container.bucket, token)).use {
            it.init()
            it.append(dummyRenogyData)
            it.append(dummyRenogyData)
            it.append(dummyRenogyData)
            val result: MutableList<FluxTable> = client.queryApi.query(fluxQuery)
            expect(true) { result.size in 1..3 }
            expect(100L) { result[0].records[0].value }
        }
    }
}
