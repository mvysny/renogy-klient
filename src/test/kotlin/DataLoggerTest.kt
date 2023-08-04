import clients.dummyRenogyData
import com.github.mvysny.dynatest.DynaTest
import org.testcontainers.DockerClientFactory
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
        influxDBDataLoggerTests()
    }
})
