package clients

val dummySystemInfo = SystemInfo(24, 40, 40, ProductType.Controller, "RENOGY ROVER", "v1.2.3", "v4.5.6", "1501FFFF")
val dummyPowerStatus = PowerStatus(100.toUShort(), 25.6f, 2.3f, 23, 23, 0f, 0f, 0u, 60.2f, 4.2f, (60.2f * 4.2f).toInt().toUShort())
val dummyDailyStats = DailyStats(25.0f, 28.0f, 10.0f, 10.0f, 240u, 240u, 100u, 100u, 0u, 0u)
val dummyHistoricalData = HistoricalData(20u, 1u, 20u, 2000u, 2000u, 2000u, 2000u)
val dummyStatus = RenogyStatus(false, 0u, ChargingState.MpptChargingMode, setOf(
    ControllerFaults.ControllerTemperatureTooHigh))
val dummyRenogyData = RenogyData(
    dummySystemInfo,
    dummyPowerStatus,
    dummyDailyStats,
    dummyHistoricalData,
    dummyStatus
)
