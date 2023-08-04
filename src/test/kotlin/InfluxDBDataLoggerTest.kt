import clients.dummyRenogyData
import com.github.mvysny.dynatest.DynaNodeGroup
import com.github.mvysny.dynatest.DynaTestDsl
import org.testcontainers.containers.InfluxDBContainer
import org.testcontainers.utility.DockerImageName

@DynaTestDsl
fun DynaNodeGroup.influxDBDataLoggerTests() {
    group("InfluxDB2DataLogger") {
        val token = "tgPiZMSv30US40AX_v9zV-dTexHeJ1u4zNCQYEGNW13DNbLiCUFxpVLPZZtX7C0f8UfN84oS3jRxaKWlAICKmA=="
        lateinit var container: InfluxDBContainer<*>
        beforeGroup {
            container = InfluxDBContainer(DockerImageName.parse("influxdb:2.7.1"))
            container.withAdminToken(token)
            container.start()
        }
        afterGroup { container.stop() }
        // @todo delete the data between the tests
        test("smoke") {
            InfluxDB2Logger(container.url, container.organization, container.bucket, token).use {
                it.init()
                it.append(dummyRenogyData)
            }
        }
        test("upsert") {
            InfluxDB2Logger(container.url, container.organization, container.bucket, token).use {
                it.init()
                it.append(dummyRenogyData)
                it.append(dummyRenogyData)
                it.append(dummyRenogyData)
            }
        }
        // @todo select the data to check that they have been written
    }
}