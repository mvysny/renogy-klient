import clients.dummyRenogyData
import com.github.mvysny.dynatest.DynaTest
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
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
    if (DockerClientFactory.instance().isDockerAvailable()) {
        group("PostgresDataLogger") {
            lateinit var container: PostgreSQLContainer<*>
            beforeGroup {
                container = PostgreSQLContainer("postgres:10.3")
                container.start()
            }
            afterGroup { container.stop() }
            test("smoke") {
                PostgresDataLogger(container.jdbcUrl, container.username, container.password).use {
                    it.init()
                    it.append(dummyRenogyData)
                }
            }
        }
    }
})
