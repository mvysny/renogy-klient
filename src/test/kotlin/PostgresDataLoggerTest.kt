import clients.dummyRenogyData
import com.github.mvysny.dynatest.DynaNodeGroup
import com.github.mvysny.dynatest.DynaTestDsl
import org.testcontainers.containers.PostgreSQLContainer

@DynaTestDsl
fun DynaNodeGroup.postgresDataLoggerTests() {
    group("PostgresDataLogger") {
        lateinit var container: PostgreSQLContainer<*>
        beforeGroup {
            container = PostgreSQLContainer("postgres:15.3")
            container.start()
        }
        afterGroup { container.stop() }
        // @todo delete the data between the tests
        test("smoke") {
            PostgresDataLogger(container.jdbcUrl, container.username, container.password).use {
                it.init()
                it.append(dummyRenogyData)
            }
        }
        // tests https://github.com/mvysny/renogy-klient/issues/1
        test("upsert") {
            PostgresDataLogger(container.jdbcUrl, container.username, container.password).use {
                it.init()
                it.append(dummyRenogyData)
                it.append(dummyRenogyData)
                it.append(dummyRenogyData)
            }
        }
        // @todo select the data to check that they have been written
    }
}
