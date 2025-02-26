package datalogger

import clients.RenogyData
import java.time.Instant

class DummyDataLogger : DataLogger {
    var inited: Boolean = false
    override fun init() {
        checkNotClosed()
        check(!inited) { "Already inited" }
        inited = true
    }

    val data = mutableListOf<RenogyData>()
    override fun append(data: RenogyData, sampledAt: Instant) {
        checkNotClosed()
        this.data.add(data)
    }

    var deleteRequested = false
    override fun deleteRecordsOlderThan(days: Int) {
        checkNotClosed()
        deleteRequested = true
    }

    var closed = false
    override fun close() {
        checkNotClosed()
        closed = true
    }

    private fun checkNotClosed() {
        check(!closed) { "Closed" }
    }
}