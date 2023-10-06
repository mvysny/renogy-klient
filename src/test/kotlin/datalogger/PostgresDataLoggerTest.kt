package datalogger

import clients.dummyRenogyData
import com.github.mvysny.dynatest.DynaNodeGroup
import com.github.mvysny.dynatest.DynaTestDsl
import com.github.vokorm.KEntity
import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.JdbiOrm
import com.gitlab.mvysny.jdbiorm.Table
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.mapper.reflect.ColumnName
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.expect

@DynaTestDsl
fun DynaNodeGroup.postgresDataLoggerTests() {
    group("PostgresDataLogger") {
        lateinit var container: PostgreSQLContainer<*>
        beforeGroup {
            container = PostgreSQLContainer("postgres:15.3")
            container.start()
            val cfg = HikariConfig().apply {
                jdbcUrl = container.getJdbcUrl()
                username = container.username
                password = container.password
            }
            JdbiOrm.setDataSource(HikariDataSource(cfg))
        }
        afterGroup {
            JdbiOrm.destroy()
            container.stop()
        }
        var firstTest = true
        beforeEach { if (firstTest) firstTest = false else PostgresDataLog.deleteAll() }
        afterEach { PostgresDataLog.deleteAll() }

        test("smoke") {
            PostgresDataLogger(container.jdbcUrl, container.username, container.password).use {
                it.init()
                it.append(dummyRenogyData)
                val logs = PostgresDataLog.findAll()
                expect(1) { logs.size }
                expect(100) { logs[0].batterySOC }
                expect(25.6f) { logs[0].batteryVoltage }
                it.deleteRecordsOlderThan()
            }
        }
        // tests https://github.com/mvysny/renogy-klient/issues/1
        test("upsert") {
            PostgresDataLogger(container.jdbcUrl, container.username, container.password).use {
                it.init()
                it.append(dummyRenogyData)
                it.append(dummyRenogyData)
                it.append(dummyRenogyData)
                val logs = PostgresDataLog.findAll()
                expect(true) { logs.size in 1..3 }
                expect(100) { logs[0].batterySOC }
                expect(25.6f) { logs[0].batteryVoltage }
            }
        }
    }
}

@Table("log")
data class PostgresDataLog(
    @field:ColumnName("DateTime")
    override var id: Long? = null,
    @field:ColumnName("BatterySOC")
    var batterySOC: Int = 0,
    @field:ColumnName("BatteryVoltage")
    var batteryVoltage: Float = 0f,
): KEntity<Long> {
    companion object : Dao<PostgresDataLog, Long>(PostgresDataLog::class.java)
}
