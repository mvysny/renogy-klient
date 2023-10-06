import clients.*
import utils.Log
import utils.scheduleAtFixedRate
import utils.scheduleAtTimeOfDay
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

fun main(_args: Array<String>) {
    val args = Args.parse(_args)

    args.newDataLogger().use { dataLogger ->
        val client: RenogyClient = if (args.isDummy) DummyRenogyClient() else FixDailyStatsClient(RetryOnTimeoutClient(args.device!!, 1.seconds))
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

    val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    scheduler.scheduleAtTimeOfDay(LocalTime.MIDNIGHT) {
        try {
            dataLogger.deleteRecordsOlderThan(args.pruneLog)
        } catch (e: Exception) {
            log.error("Failed to prune old records", e)
        }
    }

    scheduler.scheduleAtFixedRate(args.pollInterval.seconds) {
        try {
            log.debug("Getting all data from $client")
            val allData: RenogyData = client.getAllData(systemInfo)
            log.debug("Writing data to ${args.stateFile}")
            args.stateFile.writeText(allData.toJson())
            dataLogger.append(allData)
            log.debug("Main loop: done")
        } catch (e: Exception) {
            // don't crash on exception; print it out and continue. The KeepOpenClient will recover for serialport errors.
            log.warn("Main loop failure", e)
        }
    }

    log.info("Press ENTER to end the program")
    System.`in`.read()
    log.info("Terminating")
    scheduler.shutdown()
    scheduler.awaitTermination(10, TimeUnit.SECONDS)
}
