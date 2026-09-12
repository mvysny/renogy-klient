package clients

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.expect

/**
 * A [RenogyClient] returning whatever [data] currently holds.
 */
private class FakeRenogyClient(var data: RenogyData = dummyRenogyData) : RenogyClient {
    override fun getSystemInfo(): SystemInfo = data.systemInfo
    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData = data
    override fun close() {}
}

/**
 * Drives [FixDailyStatsClient] through the Renogy day: the device resets its daily stats at some
 * arbitrary hour, so between midnight and that reset we must compute the daily stats ourselves.
 */
class FixDailyStatsClientTest {
    private val fake = FakeRenogyClient()
    private var today: LocalDate = LocalDate.of(2024, 3, 1)
    private val client = FixDailyStatsClient(fake) { today }

    /**
     * Feeds one sample through the client and returns the corrected daily stats.
     */
    private fun sample(
        powerGenerationWh: UShort,
        batteryVoltage: Float = 25.6f,
        chargingCurrent: Float = 2.3f,
        solarPanelPower: UShort = 252u,
        chargingAh: UShort = 100u
    ): DailyStats {
        fake.data = dummyRenogyData.copy(
            powerStatus = dummyPowerStatus.copy(
                batteryVoltage = batteryVoltage,
                chargingCurrentToBattery = chargingCurrent,
                solarPanelPower = solarPanelPower
            ),
            dailyStats = dummyDailyStats.copy(
                powerGenerationWh = powerGenerationWh,
                chargingAh = chargingAh
            )
        )
        return client.getAllData().dailyStats
    }

    @Test fun `passes the daily stats through while the day doesn't change`() {
        expect(100u.toUShort()) { sample(100u).powerGenerationWh }
        expect(150u.toUShort()) { sample(150u).powerGenerationWh }
        expect(100u.toUShort()) { sample(150u).chargingAh }
    }

    @Test fun `a Renogy reset without a preceding midnight is passed through unchanged`() {
        sample(150u)
        // the client was started mid-day, so there is no "Don't Trust Renogy" period to offset for
        expect(5u.toUShort()) { sample(5u).powerGenerationWh }
        expect(20u.toUShort()) { sample(20u).powerGenerationWh }
    }

    @Test fun `after midnight the power generation is offset by Renogy's stale value`() {
        sample(100u)
        today = today.plusDays(1)
        // Renogy hasn't reset yet: it still reports yesterday's cumulative 110 Wh, so today's is 0
        expect(0u.toUShort()) { sample(110u).powerGenerationWh }
        expect(10u.toUShort()) { sample(120u).powerGenerationWh }
        expect(15u.toUShort()) { sample(125u).powerGenerationWh }
    }

    @Test fun `chargingAh is zeroed out during the Don't Trust Renogy period`() {
        sample(100u, chargingAh = 100u)
        today = today.plusDays(1)
        expect(0u.toUShort()) { sample(110u, chargingAh = 100u).chargingAh }
    }

    @Test fun `we compute the min-max values ourselves during the Don't Trust Renogy period`() {
        sample(100u)
        today = today.plusDays(1)
        sample(110u, batteryVoltage = 25f, chargingCurrent = 2f, solarPanelPower = 200u)
        sample(112u, batteryVoltage = 28f, chargingCurrent = 5f, solarPanelPower = 400u)
        val stats = sample(114u, batteryVoltage = 22f, chargingCurrent = 1f, solarPanelPower = 100u)
        expect(22f) { stats.batteryMinVoltage }
        expect(28f) { stats.batteryMaxVoltage }
        expect(5f) { stats.maxChargingCurrent }
        expect(400u.toUShort()) { stats.maxChargingPower }
    }

    @Test fun `the generation during the Don't Trust Renogy period is added back after Renogy resets`() {
        sample(100u)
        today = today.plusDays(1)
        sample(110u)   // midnight: powerGenerationAtMidnight = 110
        sample(130u)   // we report 20 Wh generated so far today
        // Renogy finally resets its daily stats; the 20 Wh we already counted must not be lost
        expect(20u.toUShort()) { sample(0u).powerGenerationWh }
        expect(25u.toUShort()) { sample(5u).powerGenerationWh }
    }

    @Test fun `no offset when Renogy resets in the very same sample that crosses midnight`() {
        sample(100u)
        today = today.plusDays(1)
        // the "Don't Trust Renogy" period had zero duration, so there is nothing to add back
        expect(5u.toUShort()) { sample(5u).powerGenerationWh }
        expect(20u.toUShort()) { sample(20u).powerGenerationWh }
    }

    @Test fun `everything but the daily stats is passed through untouched`() {
        sample(100u)
        today = today.plusDays(1)
        sample(110u)
        val data = client.getAllData()
        expect(dummySystemInfo) { data.systemInfo }
        expect(fake.data.powerStatus) { data.powerStatus }
        expect(dummyHistoricalData) { data.historicalData }
        expect(dummyStatus) { data.status }
    }

    @Test fun `getSystemInfo is delegated`() {
        expect(dummySystemInfo) { client.getSystemInfo() }
    }
}
