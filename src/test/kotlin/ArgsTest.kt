import datalogger.CSVDataLogger
import datalogger.CompositeDataLogger
import datalogger.PostgresDataLogger
import datalogger.RetryableDataLogger
import datalogger.StdoutCSVDataLogger
import datalogger.TimeoutDataLogger
import org.junit.jupiter.api.Test
import utils.Log
import utils.closeQuietly
import java.io.File
import kotlin.test.expect
import kotlin.test.fail

class ArgsTest {
    private fun args(vararg argv: String): Args = Args.parse(arrayOf(*argv))

    @Test fun `the defaults`() {
        val args = args("/dev/ttyUSB0")
        expect(File("/dev/ttyUSB0")) { args.device }
        expect(1.toByte()) { args.deviceAddress }
        expect(false) { args.printStatusOnly }
        expect(false) { args.utc }
        expect(null) { args.csv }
        expect(null) { args.postgres }
        expect(null) { args.influx }
        expect(File("status.json")) { args.stateFile }
        expect(10) { args.pollInterval }
        expect(365) { args.pruneLog }
        expect(false) { args.isDummy }
    }

    @Test fun `the dummy device is recognized`() {
        expect(true) { args("dummy").isDummy }
    }

    @Test fun `the options are parsed`() {
        val args = args(
            "dummy", "--utc", "--status", "--verbose",
            "--device-address", "3",
            "--csv", "/tmp/foo.csv",
            "--statefile", "/tmp/state.json",
            "-i", "20",
            "--prunelog", "30"
        )
        expect(true) { args.utc }
        expect(true) { args.printStatusOnly }
        expect(true) { args.verbose }
        expect(3.toByte()) { args.deviceAddress }
        expect(File("/tmp/foo.csv")) { args.csv }
        expect(File("/tmp/state.json")) { args.stateFile }
        expect(20) { args.pollInterval }
        expect(30) { args.pruneLog }
    }

    @Test fun `--verbose flips the debug logging`() {
        val originalDebug = Log.isDebugEnabled
        try {
            args("dummy")
            expect(false) { Log.isDebugEnabled }
            args("dummy", "--verbose")
            expect(true) { Log.isDebugEnabled }
        } finally {
            Log.isDebugEnabled = originalDebug
        }
    }

    // validate() is exercised directly: a malformed command line makes Args.parse() call
    // exitProcess(), which would take the test JVM down with it.
    @Test fun `a non-positive poll interval is rejected`() {
        try {
            Args(device = File("dummy"), pollInterval = 0).validate()
            fail("Expected to fail")
        } catch (e: IllegalArgumentException) {
            expect("pollInterval: must be 1 or greater but was 0") { e.message }
        }
    }

    @Test fun `a non-positive prune period is rejected`() {
        try {
            Args(device = File("dummy"), pruneLog = -1).validate()
            fail("Expected to fail")
        } catch (e: IllegalArgumentException) {
            expect("pruneLog: must be 1 or greater but was -1") { e.message }
        }
    }

    @Test fun `stdout logging by default`() {
        args("dummy").newDataLogger().use { logger ->
            val loggers = (logger as CompositeDataLogger).dataLoggers
            expect(1) { loggers.size }
            expect(true) { loggers[0] is StdoutCSVDataLogger }
        }
    }

    @Test fun `--csv replaces the stdout logger`() {
        val csv = File.createTempFile("renogy", ".csv")
        csv.delete()
        try {
            args("dummy", "--csv", csv.absolutePath).newDataLogger().use { logger ->
                val loggers = (logger as CompositeDataLogger).dataLoggers
                expect(1) { loggers.size }
                expect(true) { loggers[0] is CSVDataLogger }
            }
        } finally {
            csv.delete()
        }
    }

    /**
     * The CSV file is logged to directly, but a database may block and must therefore be wrapped;
     * see `R_poll_never_blocks`.
     */
    @Test fun `a database logger is wrapped in retry plus timeout`() {
        val logger = args("dummy", "--postgres", "jdbc:postgresql://localhost:5432/postgres").newDataLogger()
        try {
            val loggers = (logger as CompositeDataLogger).dataLoggers
            expect(1) { loggers.size }
            val retryable = loggers[0] as RetryableDataLogger
            val timeout = retryable.delegate as TimeoutDataLogger
            expect(true) { timeout.delegate is PostgresDataLogger }
        } finally {
            logger.closeQuietly()   // the pool was never opened, so closing it blows up
        }
    }

    @Test fun `several loggers can be combined`() {
        val csv = File.createTempFile("renogy", ".csv")
        csv.delete()
        val logger = args(
            "dummy",
            "--csv", csv.absolutePath,
            "--postgres", "jdbc:postgresql://localhost:5432/postgres"
        ).newDataLogger()
        try {
            expect(2) { (logger as CompositeDataLogger).dataLoggers.size }
        } finally {
            logger.closeQuietly()
            csv.delete()
        }
    }

    @Test fun `influx without its mandatory options is rejected`() {
        for (missing in listOf("--influxorg", "--influxbucket", "--influxtoken")) {
            val argv = mutableListOf("dummy", "--influx", "http://localhost:8086",
                "--influxorg", "org", "--influxbucket", "bucket", "--influxtoken", "token")
            val at = argv.indexOf(missing)
            argv.subList(at, at + 2).clear()
            try {
                args(*argv.toTypedArray()).newDataLogger()
                fail("Expected $missing to be mandatory")
            } catch (e: IllegalArgumentException) {
                expect(true, e.message) { e.message!!.contains(missing.removePrefix("--")) }
            }
        }
    }
}
