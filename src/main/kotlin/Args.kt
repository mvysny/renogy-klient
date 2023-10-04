import org.slf4j.LoggerFactory
import org.slf4j.simple.SimpleLoggerConfiguration
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import utils.closeQuietly
import java.io.File
import kotlin.system.exitProcess

/**
 * @property device the file name of the serial device to communicate with, e.g. `/dev/ttyUSB0`. Pass in `dummy` for a dummy Renogy client
 * @property printStatusOnly if true, print the Renogy Rover status as JSON to stdout and quit.
 * @property utc CSV: dump date in UTC instead of local, handy for Grafana.
 * @property csv if not null, appends status to this CSV file. Disables stdout status logging.
 * @property postgres if not null, appends status to a postgresql database, disables stdout status logging. Accepts the connection url, e.g. `jdbc:postgresql://localhost:5432/postgres`
 * @property postgresUsername PostgreSQL username
 * @property postgresPassword PostgreSQL password
 * @property influx if not null, appends status to an InfluxDB2 database, disables stdout status logging. Accepts the connection url, e.g. `http://localhost:8086`
 * @property influxOrg the InfluxDB2 organization, e.g. my_org
 * @property influxBucket the InfluxDB2 bucket, e.g. solar
 * @property influxToken the InfluxDB2 access token
 * @property stateFile overwrites status to file other than the default 'status.json'
 * @property pollInterval in seconds: how frequently to poll the controller for data, defaults to 10
 * @property pruneLog Prunes log entries older than x days, defaults to 365. Applies to databases only; a CSV file is never pruned.
 * @property verbose Print verbosely what I'm doing
 */
data class Args(
    @field:Parameters(paramLabel = "DEVICE", description = ["the file name of the serial device to communicate with, e.g. `/dev/ttyUSB0`. Pass in `dummy` for a dummy Renogy client"])
    var device: File? = null,
    @field:Option(names = ["--status"], description = ["print the Renogy Rover status as JSON to stdout and quit"])
    var printStatusOnly: Boolean = false,
    @field:Option(names = ["--utc"], description = ["CSV: dump date in UTC instead of local, handy for Grafana"])
    var utc: Boolean = false,
    @field:Option(names = ["--csv"], description = ["appends status to a CSV file, disables stdout status logging"])
    var csv: File? = null,
    @field:Option(names = ["--postgres"], description = ["appends status to a postgresql database, disables stdout status logging. Accepts the connection url, e.g. jdbc:postgresql://localhost:5432/postgres"])
    var postgres: String? = null,
    @field:Option(names = ["--pguser"], description = ["PostgreSQL user name"])
    var postgresUsername: String? = null,
    @field:Option(names = ["--pgpass"], description = ["PostgreSQL password"])
    var postgresPassword: String? = null,
    @field:Option(names = ["--influx"], description = ["appends status to an InfluxDB2 database, disables stdout status logging. Accepts the connection url, e.g. `http://localhost:8086`"])
    var influx: String? = null,
    @field:Option(names = ["--influxorg"], description = ["the InfluxDB2 organization, e.g. my_org. Required if Influx is used."])
    var influxOrg: String? = null,
    @field:Option(names = ["--influxbucket"], description = ["the InfluxDB2 bucket, e.g. solar. Required if Influx is used."])
    var influxBucket: String? = null,
    @field:Option(names = ["--influxtoken"], description = ["the InfluxDB2 access token. Required if Influx is used."])
    var influxToken: String? = null,
    @field:Option(names = ["--statefile"], description = ["overwrites status to file other than the default 'status.json'"])
    var stateFile: File = "status.json".toFile(),
    @field:Option(names = ["--pollinterval", "-i"], description = ["in seconds: how frequently to poll the controller for data, defaults to 10"])
    var pollInterval: Int = 10,
    @field:Option(names = ["--prunelog"], description = ["prunes log entries older than x days, defaults to 365"])
    var pruneLog: Int = 365,
    @field:Option(names = ["--verbose"], description = ["Print verbosely what I'm doing"])
    var verbose: Boolean = false
) {
    fun validate() {
        require(pollInterval > 0) { "pollInterval: must be 1 or greater but was $pollInterval" }
        require(pruneLog > 0) { "pruneLog: must be 1 or greater but was $pruneLog" }
    }

    /**
     * If 'true' we'll feed the data from a dummy device. Useful for testing.
     */
    val isDummy: Boolean get() = device!!.path == "dummy"

    fun newDataLogger(): DataLogger {
        val result = CompositeDataLogger()
        try {
            if (csv != null) {
                result.dataLoggers.add(CSVDataLogger(csv!!, utc))
            }
            if (postgres != null) {
                result.dataLoggers.add(PostgresDataLogger(postgres!!, postgresUsername, postgresPassword))
            }
            if (influx != null) {
                requireNotNull(influxOrg) { "influxorg must be specified" }
                requireNotNull(influxBucket) { "influxbucket must be specified" }
                requireNotNull(influxToken) { "influxtoken must be specified" }
                result.dataLoggers.add(InfluxDB2Logger(influx!!, influxOrg!!, influxBucket!!, influxToken!!))
            }
            if (result.dataLoggers.isEmpty()) {
                result.dataLoggers.add(StdoutCSVDataLogger(utc))
            }
        } catch (ex: Exception) {
            result.closeQuietly()
            throw ex
        }
        return result
    }

    companion object {
        fun parse(aargs: Array<String>): Args {
            val args = Args()
            val cli = CommandLine(args)
            cli.commandName = "renogy-klient"
            try {
                cli.parseArgs(*aargs)
            } catch (e: Exception) {
                e.printStackTrace()
                cli.usage(System.out)
                exitProcess(1)
            }

            args.validate()

            if (args.verbose) {
                setDefaultLogLevelDebug()
            }
            val log = LoggerFactory.getLogger(Args::class.java)
            log.debug(args.toString())
            return args
        }
    }
}

private fun String.toFile(): File = File(this)
private fun setDefaultLogLevelDebug() {
    val f = SimpleLoggerConfiguration::class.java.getDeclaredField("DEFAULT_LOG_LEVEL_DEFAULT")
    f.isAccessible = true
    f.set(null, 10) // LocationAwareLogger.DEBUG_INT
}
