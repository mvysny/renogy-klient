import clients.dummyRenogyData
import com.github.mvysny.dynatest.DynaNodeGroup
import com.github.mvysny.dynatest.DynaTestDsl
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.query.FluxTable
import org.testcontainers.containers.InfluxDBContainer
import org.testcontainers.utility.DockerImageName
import java.time.OffsetDateTime
import kotlin.test.expect

@DynaTestDsl
fun DynaNodeGroup.influxDBDataLoggerTests() {
    group("InfluxDB2DataLogger") {
        val token = "tgPiZMSv30US40AX_v9zV-dTexHeJ1u4zNCQYEGNW13DNbLiCUFxpVLPZZtX7C0f8UfN84oS3jRxaKWlAICKmA=="
        lateinit var container: InfluxDBContainer<*>
        lateinit var client: InfluxDBClient
        beforeGroup {
            container = InfluxDBContainer(DockerImageName.parse("influxdb:2.7.1"))
            container.withAdminToken(token)
            container.start()
        }
        lateinit var fluxQuery: String
        beforeGroup {
            client = InfluxDBClientFactory.create(container.url, token.toCharArray(), container.organization, container.bucket)
            fluxQuery = ("""from(bucket: "${container.bucket}")
 |> range(start: -1d, stop: 1d)
 |> filter(fn: (r) => (r["_measurement"] == "renogy" and r["_field"] == "BatterySOC"))""")
        }
        afterGroup { client.close() }
        afterGroup { container.stop() }
        beforeEach { client.deleteApi.delete(OffsetDateTime.now().minusYears(1), OffsetDateTime.now().plusYears(1), "", container.bucket, container.organization) }

        test("smoke") {
            InfluxDB2Logger(container.url, container.organization, container.bucket, token).use {
                it.init()
                it.append(dummyRenogyData)
                val result: MutableList<FluxTable> = client.queryApi.query(fluxQuery)
                expect(1) { result.size }
                expect(100L) { result[0].records[0].value }
            }
        }
        test("upsert") {
            InfluxDB2Logger(container.url, container.organization, container.bucket, token).use {
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
}
