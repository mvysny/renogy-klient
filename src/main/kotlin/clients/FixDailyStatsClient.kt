package clients

import Log
import java.time.LocalDate

/**
 * Renogy resets the daily stats not at midnight, but at some arbitrary time during the day.
 * Currently, for me, the stats are reset at 9:17am, which is a huge wtf.
 * Therefore, we can not trust the daily data at all times. We'll detect the time period when the
 * Renogy daily stats can not be trusted, and we'll calculate them ourselves.
 * @property delegate fetch data from here.
 */
class FixDailyStatsClient(val delegate: RenogyClient) : RenogyClient by delegate {

    private var dailyStatsCalculator: DailyStatsStrategy = DailyStatsStrategy.RenogyPassThrough(0u)
    init {
        log.info("Starting with daily stats $dailyStatsCalculator")
    }

    /**
     * [DailyStats.powerGenerationWh] from Renogy's previous measurement. If the current measurement is lower,
     * Renogy has performed the daily value reset.
     */
    private var prevPowerGenerationWh: UShort? = null

    private var lastDataSampledAt: LocalDate = LocalDate.now()

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData {
        val allData: RenogyData = delegate.getAllData(cachedSystemInfo)
        val currentDailyStatsFromRenogy: DailyStats = allData.dailyStats

        // if we crossed the day barrier, force DontTrustRenogyPeriod until Renogy zeroes the daily stats out itself.
        val today = LocalDate.now()
        val crossedMidnight = lastDataSampledAt != today
        if (crossedMidnight) {
            lastDataSampledAt = today
            dailyStatsCalculator = DailyStatsStrategy.DontTrustRenogyPeriod(allData)
            log.info("Midnight: activating $dailyStatsCalculator")
        }

        // detect whether Renogy finally performed the daily value reset.
        val prevPowerGenerationWh = this.prevPowerGenerationWh
        if (prevPowerGenerationWh != null && prevPowerGenerationWh > currentDailyStatsFromRenogy.powerGenerationWh) {
            // Yes: Renogy finally performed the daily value reset. Switch back to RenogyPassThrough
            if (dailyStatsCalculator is DailyStatsStrategy.DontTrustRenogyPeriod) {
                val powerGenerationAtMidnight =
                    (dailyStatsCalculator as DailyStatsStrategy.DontTrustRenogyPeriod).powerGenerationAtMidnight
                var powerGenerationDuringDontTrustPeriod =
                    (prevPowerGenerationWh - powerGenerationAtMidnight).toUShort()
                        .coerceAtLeast(0u) // to be extra-sure, probably won't happen ever
                if (crossedMidnight) {
                    // it's midnight AND renogy reset its stats => no "Don't Trust Renogy" period => zero
                    powerGenerationDuringDontTrustPeriod = 0u
                }
                dailyStatsCalculator = DailyStatsStrategy.RenogyPassThrough(powerGenerationDuringDontTrustPeriod)
                log.info("Renogy performed the daily value reset, ending the 'Don't Trust Renogy' Period (powerGeneration: prev=$prevPowerGenerationWh,now=${currentDailyStatsFromRenogy.powerGenerationWh}); current daily stats=$dailyStatsCalculator")
            } else {
                // not in "Don't Trust Renogy" period? corner-case - perhaps the client was just launched and hasn't hit midnight yet.
                log.info("Renogy performed the daily value reset but there was no 'Don't Trust Renogy' Period, passing the daily stats through")
                dailyStatsCalculator = DailyStatsStrategy.RenogyPassThrough(0u)
            }
        }
        this.prevPowerGenerationWh = currentDailyStatsFromRenogy.powerGenerationWh

        val newDailyStats = dailyStatsCalculator.process(allData)
        return allData.copy(dailyStats = newDailyStats)
    }

    override fun toString(): String = "FixDailyStatsClient($delegate)"

    companion object {
        private val log = Log<FixDailyStatsClient>()
    }
}

private sealed class DailyStatsStrategy {
    abstract fun process(data: RenogyData): DailyStats

    /**
     * The time from midnight until 9:17am (or any other arbitrary point in time until Renogy finally resets the data)
     * is called the "Don't Trust Renogy" period. In this period, we don't trust Renogy - instead, we calculate the daily stats ourselves.
     */
    class DontTrustRenogyPeriod(midnightData: RenogyData) : DailyStatsStrategy() {
        /**
         * Cumulative power generation at midnight as reported by Renogy. We use this to offset the power generation
         * during the "Don't Trust Renogy" period.
         */
        val powerGenerationAtMidnight: UShort = midnightData.dailyStats.powerGenerationWh

        private class MyDailyStats(initialData: PowerStatus) {
            var batteryMinVoltage: Float = initialData.batteryVoltage
                private set
            var batteryMaxVoltage: Float = initialData.batteryVoltage
                private set
            var maxChargingCurrent: Float = initialData.chargingCurrentToBattery
                private set
            var maxChargingPower: UShort = initialData.solarPanelPower
                private set

            fun update(powerStatus: PowerStatus) {
                batteryMinVoltage = batteryMinVoltage.coerceAtMost(powerStatus.batteryVoltage)
                batteryMaxVoltage = batteryMaxVoltage.coerceAtLeast(powerStatus.batteryVoltage)
                maxChargingCurrent = maxChargingCurrent.coerceAtLeast(powerStatus.chargingCurrentToBattery)
                maxChargingPower = maxChargingPower.coerceAtLeast(powerStatus.solarPanelPower)
            }
        }

        /**
         * Statistics calculated by us.
         */
        private val myDailyStats: MyDailyStats = MyDailyStats(midnightData.powerStatus)

        override fun process(data: RenogyData): DailyStats {
            myDailyStats.update(data.powerStatus)
            return data.dailyStats.copy(
                batteryMinVoltage = myDailyStats.batteryMinVoltage,
                batteryMaxVoltage = myDailyStats.batteryMaxVoltage,
                maxChargingCurrent = myDailyStats.maxChargingCurrent,
                maxChargingPower = myDailyStats.maxChargingPower,
                chargingAh = 0u,
                powerGenerationWh = (data.dailyStats.powerGenerationWh - powerGenerationAtMidnight).toUShort()
            )
        }

        override fun toString(): String =
            "DontTrustRenogyPeriod(powerGenerationAtMidnight=$powerGenerationAtMidnight)"
    }

    /**
     * @property powerGenerationDuringDontTrustPeriod Cumulative power generation during the [DontTrustRenogyPeriod] period. We'll add this value to [DailyStats.powerGenerationWh]
     * when outside of the "Don't Trust Renogy" period, to offset for power generation during the [DontTrustRenogyPeriod].
     */
    class RenogyPassThrough(val powerGenerationDuringDontTrustPeriod: UShort) : DailyStatsStrategy() {
        override fun process(data: RenogyData): DailyStats = when (powerGenerationDuringDontTrustPeriod) {
            0.toUShort() -> data.dailyStats
            else -> data.dailyStats.copy(powerGenerationWh = (data.dailyStats.powerGenerationWh + powerGenerationDuringDontTrustPeriod).toUShort())
        }

        override fun toString(): String =
            "RenogyPassThrough(powerGenerationDuringDontTrustPeriod=$powerGenerationDuringDontTrustPeriod)"
    }
}
