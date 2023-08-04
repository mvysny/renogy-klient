import clients.dummyRenogyData
import com.github.mvysny.dynatest.DynaTest
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.InfluxDBContainer
import org.testcontainers.utility.DockerImageName
import java.io.File

class DataLoggerTest : DynaTest({
    group("CSVDataLogger") {
        test("smoke") {
            CSVDataLogger(File.createTempFile("temp", "csv"), true).use {
                it.init()
                it.append(dummyRenogyData)
            }
        }
    }
    group("StdoutCSVDataLogger") {
        test("smoke") {
            StdoutCSVDataLogger(false).use {
                it.init()
                it.append(dummyRenogyData)
            }
        }
    }
    if (DockerClientFactory.instance().isDockerAvailable) {
        postgresDataLoggerTests()
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
})
