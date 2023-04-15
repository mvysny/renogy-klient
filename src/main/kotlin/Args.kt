import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import org.slf4j.LoggerFactory
import org.slf4j.simple.SimpleLoggerConfiguration
import utils.closeQuietly
import java.io.File

/**
 * @property device the file name of the serial device to communicate with, e.g. `/dev/ttyUSB0`. Pass in `dummy` for a dummy Renogy client
 * @property printStatusOnly if true, print the Renogy Rover status as JSON to stdout and quit.
 * @property utc CSV: dump date in UTC instead of local, handy for Grafana.
 * @property csv if not null, appends status to this CSV file. Disables stdout status logging.
 * @property postgres if not null, appends status to a postgresql database, disables stdout status logging. Accepts the connection url, e.g. `postgresql://user:pass@localhost:5432/postgres`
 * @property stateFile overwrites status to file other than the default 'status.json'
 * @property pollInterval in seconds: how frequently to poll the controller for data, defaults to 10
 * @property pruneLog Prunes log entries older than x days, defaults to 365. Applies to databases only; a CSV file is never pruned.
 * @property verbose Print verbosely what I'm doing
 */
data class Args(
    val device: File,
    val printStatusOnly: Boolean,
    val utc: Boolean,
    val csv: File?,
    val postgres: String?,
    val stateFile: File,
    val pollInterval: Int,
    val pruneLog: Int,
    val verbose: Boolean
) {
    init {
        require(pollInterval > 0) { "pollInterval: must be 1 or greater but was $pollInterval" }
        require(pruneLog > 0) { "pruneLog: must be 1 or greater but was $pruneLog" }
    }

    /**
     * If 'true' we'll feed the data from a dummy device. Useful for testing.
     */
    val isDummy: Boolean get() = device.path == "dummy"

    fun newDataLogger(): DataLogger {
        val result = CompositeDataLogger()
        try {
            if (csv != null) {
                result.dataLoggers.add(CSVDataLogger(csv, utc))
            }
            if (postgres != null) {
                result.dataLoggers.add(PostgresDataLogger(postgres))
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
        fun parse(args: Array<String>): Args {
            val parser = ArgParser("solar-controller-client")
            val device by parser.argument(ArgType.String, description = "the file name of the serial device to communicate with, e.g. /dev/ttyUSB0 . Pass in `dummy` for a dummy Renogy client")
            val status by parser.option(ArgType.Boolean, fullName = "status", description = "print the Renogy Rover status as JSON to stdout and quit")
            val utc by parser.option(ArgType.Boolean, fullName = "utc", description = "CSV: dump date in UTC instead of local, handy for Grafana")
            val csv by parser.option(ArgType.String, fullName = "csv", description = "appends status to a CSV file, disables stdout status logging")
            val postgres by parser.option(ArgType.String, fullName = "postgres", description = "appends status to a postgresql database, disables stdout status logging. Accepts the connection url, e.g. postgresql://user:pass@localhost:5432/postgres")
            val statefile by parser.option(ArgType.String, fullName = "statefile", description = "overwrites status to file other than the default 'status.json'")
            val pollingInterval by parser.option(ArgType.Int, fullName = "pollinterval", shortName = "i", description = "in seconds: how frequently to poll the controller for data, defaults to 10")
            val pruneLog by parser.option(ArgType.Int, fullName = "prunelog", description = "prunes log entries older than x days, defaults to 365")
            val verbose by parser.option(ArgType.Boolean, fullName = "verbose", description = "Print verbosely what I'm doing")
            parser.parse(args)

            val args = Args(
                device.toFile(),
                status == true,
                utc == true,
                csv?.toFile(),
                postgres,
                (statefile ?: "status.json").toFile(),
                pollingInterval ?: 10,
                pruneLog ?: 365,
                verbose ?: false
            )

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
