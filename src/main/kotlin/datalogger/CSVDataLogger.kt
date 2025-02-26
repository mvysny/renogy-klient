package datalogger

import clients.RenogyData
import utils.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.Instant

/**
 * Logs [RenogyData] to a CSV file.
 */
class CSVDataLogger(val file: File, val utc: Boolean) : DataLogger {
    private lateinit var out: PrintStream
    private lateinit var csv: CSVRenogyWriter
    override fun init() {
        if (!file.exists()) {
            out = PrintStream(file, Charsets.UTF_8)
            csv = CSVRenogyWriter(out)
            csv.writeHeader()
        } else {
            out =
                PrintStream(FileOutputStream(file, true), false, Charsets.UTF_8)
            csv = CSVRenogyWriter(out)
        }
    }

    override fun append(data: RenogyData, sampledAt: Instant) {
        csv.writeData(data, utc)
    }

    override fun deleteRecordsOlderThan(days: Int) {
        // it would take too much time to process a huge CSV file; also CSV is considered experimental, so don't bother
        log.info("Record cleanup not implemented for CSV")
    }

    override fun toString(): String = "CSVDataLogger($file, utc=$utc)"

    override fun close() {
        out.close()
    }

    companion object {
        private val log = Log<CSVDataLogger>()
    }
}