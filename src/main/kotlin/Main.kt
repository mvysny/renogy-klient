import clients.*
import datalogger.DataLogger
import utils.Log
import utils.scheduleAtFixedRate
import utils.scheduleAtTimeOfDay
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

fun main(_args: Array<String>) {
    val args = Args.parse(_args)

    args.newDataLogger().use { dataLogger ->
        val timeout = 3.seconds // 1.seconds is too small, the app often times out on startup
        val client: RenogyClient = if (args.isDummy) DummyRenogyClient() else FixDailyStatsClient(RetryOnTimeoutClient(args.device!!, timeout))
        client.use {
            if (args.printStatusOnly) {
                val allData: RenogyData = client.getAllData()
                println(allData.toJson())
            } else {
                mainLoop(client, args, dataLogger)
            }
        }
    }
}

object Main {
    lateinit var scheduler: ScheduledExecutorService
}

/**
 * Runs the main loop: periodically polls [client] for new Solar Controller data,
 * then logs the data to the [dataLogger].
 */
private fun mainLoop(
    client: RenogyClient,
    args: Args,
    dataLogger: DataLogger
) {
    val log = Log("Main")
    log.info("Accessing solar controller via $client")
    val systemInfo: SystemInfo = client.getSystemInfo()
    log.info("Solar Controller: $systemInfo")
    log.info("Polling the solar controller every ${args.pollInterval} seconds; writing status to ${args.stateFile}, appending data to $dataLogger")

    dataLogger.init()
    dataLogger.deleteRecordsOlderThan(args.pruneLog)

    // don't use Executors.newSingleThreadScheduledExecutor() - if InfluxDBTinyClient blocks on a TCP-IP or such,
    // don't prevent the main loop from being executed.
    Main.scheduler = Executors.newScheduledThreadPool(1)
    Main.scheduler.scheduleAtTimeOfDay(LocalTime.MIDNIGHT) {
        try {
            dataLogger.deleteRecordsOlderThan(args.pruneLog)
        } catch (e: Exception) {
            log.error("Failed to prune old records", e)
        }
    }

    val dataGrabLock: Lock = ReentrantLock()
    Main.scheduler.scheduleAtFixedRate(args.pollInterval.seconds) {
        try {
            log.debug("Getting all data from $client")
            val allData: RenogyData = dataGrabLock.withLock { client.getAllData(systemInfo) }
            log.debug("Writing data to ${args.stateFile}")
            args.stateFile.writeText(allData.toJson())
            // log data asynchronously - if there's a timeout or such, just repeat it a couple of times but don't
            // delay the data sampling.
            Main.scheduler.submit {
                try {
                    log.debug("Logging data to data loggers")
                    dataLogger.append(allData)
                    log.debug("Data logged")
                } catch (t: Throwable) {
                    log.error("Failed to $dataLogger", t)
                }
            }
            log.debug("Main loop: done")
        } catch (e: Exception) {
            // don't crash on exception; print it out and continue. The KeepOpenClient will recover for serialport errors.
            log.warn("Main loop failure", e)
        }
    }

    log.info("Press ENTER to end the program")
    System.`in`.read()
    log.info("Terminating with the timeout of 10 seconds")
    Main.scheduler.shutdown()
    Main.scheduler.awaitTermination(10, TimeUnit.SECONDS)
    log.info("Killing all unresponsive threads")
    Main.scheduler.shutdownNow()
    log.debug("Done")
}
