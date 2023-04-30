package clients

import clients.RenogyException.Companion.fromCode
import kotlinx.serialization.Serializable
import java.io.Closeable

interface RenogyClient : Closeable {
    /**
     * Retrieves the [SystemInfo] from the device.
     */
    fun getSystemInfo(): SystemInfo

    /**
     * Retrieves all current data from a Renogy device. Usually [SystemInfo] is only
     * fetched once and then cached; it can be passed in as [cachedSystemInfo]
     * to avoid repeated retrieval.
     * @param cachedSystemInfo if not null, this information will not be fetched.
     * @throws RenogyException if the data retrieval fails
     */
    fun getAllData(cachedSystemInfo: SystemInfo? = null): RenogyData
}

/**
 * @param streetLightOn Whether the street light is on or off
 * @param streetLightBrightness street light brightness value, 0..100 in %
 * @param chargingState charging state (if known)
 * @param faults current faults, empty if none.
 */
@Serializable
data class RenogyStatus(
    val streetLightOn: Boolean,
    val streetLightBrightness: UByte,
    val chargingState: ChargingState?,
    val faults: Set<ControllerFaults>
)

enum class ChargingState(val value: UByte) {
    /**
     * Charging is deactivated. There is no current/voltage detected from the solar panels.
     * This happens when it's night outside, or the solar array is disconnected:
     * either the fuse tripped, or perhaps the cables are broken.
     */
    ChargingDeactivated(0u),
    ChargingActivated(1u),

    /**
     * Bulk Charging. This algorithm is used for day to day charging. It uses 100% of available solar
     * power to recharge the battery and is equivalent to constant current. In this stage the battery
     * voltage has not yet reached constant voltage (Equalize or Boost), the controller operates in
     * constant current mode, delivering its maximum current to the batteries (MPPT Charging).
     */
    MpptChargingMode(2u),

    /**
     * Equalization: Is carried out every 28 days of the month. It is intentional overcharging of
     * the battery for a controlled period of time. Certain types of batteries benefit from periodic
     * equalizing charge, which can stir the electrolyte, balance battery voltage and complete
     * chemical reaction. Equalizing charge increases the battery voltage, higher than the standard
     * complement voltage, which gasifies the battery electrolyte.
     *
     * Should not be used for AGM batteries.
     */
    EqualizingChargingMode(3u),

    /**
     * Constant Charging Mode. When the battery reaches the constant voltage set point, the controller
     * will start to operate in constant charging mode, where it is no longer MPPT charging. The current
     * will drop gradually. This has two stages, equalize and boost and they are not carried out
     * constantly in a full charge process to avoid too much gas precipitation or overheating of the
     * battery. See [EqualizingChargingMode] for more details.
     *
     * Boost stage maintains a charge for 2 hours by default. The user
     * can adjust the constant time and preset value of boost per their demand.
     */
    BoostChargingMode(4u),

    /**
     * After the constant voltage stage ([BoostChargingMode]/[EqualizingChargingMode]), the controller will reduce the battery voltage
     * to a float voltage set point. Once the battery is fully charged, there will be no more chemical
     * reactions and all the charge current would turn into heat or gas. Because of this,
     * the charge controller will reduce the voltage charge to smaller quantity, while lightly charging
     * the battery. The purpose for this is to offset the power consumption while maintaining a full
     * battery storage capacity. In the event that a load drawn from the battery exceeds the charge
     * current, the controller will no longer be able to maintain the battery to a Float set point and the
     * controller will end the float charge stage and refer back to bulk charging ([MpptChargingMode]).
     */
    FloatingChargingMode(5u),
    /**
     * Current limiting (overpower)
     */
    CurrentLimiting(6u),
    ;
    companion object {
        fun fromModbus(value: UByte): ChargingState? =
            values().firstOrNull { it.value == value }
    }
}

enum class ControllerFaults(val bit: Int) {
    ChargeMOSShortCircuit(30),
    AntiReverseMOSShort(29),

    /**
     * PV Reverse Polarity. The controller will not operate if the PV wires are switched.
     * Wire them correctly to resume normal controller operation.
     */
    SolarPanelReverselyConnected(28),
    SolarPanelWorkingPointOverVoltage(27),
    SolarPanelCounterCurrent(26),

    /**
     * PV Overvoltage. If the PV voltage is larger than maximum input open voltage 100VDC.
     * PV will remain disconnected until the voltage drops below 100VDC.
     */
    PhotovoltaicInputSideOverVoltage(25),

    /**
     * PV Array Short Circuit. When PV short circuit occurs, the controller will stop
     * charging. Clear it to resume normal operation.
     */
    PhotovoltaicInputSideShortCircuit(24),

    /**
     * PV Overcurrent. The controller will limit the battery chgarging current to the
     * maximum battery current rating. Therefore, an over-sized solar array will not operate at peak power.
     */
    PhotovoltaicInputOverpower(23),
    AmbientTemperatureTooHigh(22),
    /**
     * Over-Temperature. If the temperature of the controller heat sink exceeds 65 C,
     * the controller will automatically start reducing the charging current. The controller will
     * shut down when the temperature exceeds 85 C.
     */
    ControllerTemperatureTooHigh(21),

    /**
     * Load Overload. If the current exceeds the maximum load current rating 1.05 times,
     * the controller will disconnect the load. Overloading must be cleared up by reducing the
     * load and restarting the controller.
     */
    LoadOverpowerOrLoadOverCurrent(20),

    /**
     * Load Short Circuit. Fully protected against the load wiring short-circuit. Once the load
     * short (more than quadruple rate current), the load short protection will start automatically.
     * After 5 automatic load reconnect attempts, the faults must be cleared by restarting the controller.
     */
    LoadShortCircuit(19),
    BatteryUnderVoltageWarning(18),
    BatteryOverVoltage(17),
    BatteryOverDischarge(16),
    ;

    private fun isPresent(modbusValue: UInt): Boolean {
        val probe = 1.shl(bit)
        return (modbusValue.toInt() and probe) != 0
    }

    companion object {
        fun fromModbus(modbusValue: UInt): Set<ControllerFaults> =
            values().filter { it.isPresent(modbusValue) } .toSet()
    }
}

/**
 * Historical data summary
 * @param daysUp Total number of operating days
 * @param batteryOverDischargeCount Total number of battery over-discharges
 * @param batteryFullChargeCount Total number of battery full-charges.
 * @param totalChargingBatteryAH Total charging amp-hrs of the battery.
 * @param totalDischargingBatteryAH Total discharging amp-hrs of the battery. mavi: probably only applicable to inverters, 0 for controller.
 * @param cumulativePowerGenerationWH cumulative power generation in Wh. Probably only applies to controller, will be 0 for inverter.
 * @param cumulativePowerConsumptionWH cumulative power consumption in Wh. mavi: probably only applicable to inverters, 0 for controller.
 */
@Serializable
data class HistoricalData(
    val daysUp: UShort,
    val batteryOverDischargeCount: UShort,
    val batteryFullChargeCount: UShort,
    val totalChargingBatteryAH: UInt,
    val totalDischargingBatteryAH: UInt,
    val cumulativePowerGenerationWH: UInt,
    val cumulativePowerConsumptionWH: UInt
)

/**
 * The daily statistics.
 * @param batteryMinVoltage Battery's min. voltage of the current day, V. Precision: 1 decimal points.
 * @param batteryMaxVoltage Battery's max. voltage of the current day, V. Precision: 1 decimal points.
 * @param maxChargingCurrent Max. charging current of the current day, A. Probably applies to controller only. Precision: 2 decimal points.
 * @param maxDischargingCurrent Max. discharging current of the current day, A. mavi: probably only applies to inverter; will be 0 for controller. Precision: 2 decimal points.
 * @param maxChargingPower Max. charging power of the current day, W. mavi: probably only applies to controller; will be 0 for inverter.
 * @param maxDischargingPower Max. discharging power of the current day, W. mavi: probably only applies to inverter; will be 0 for controller.
 * @param chargingAh Charging amp-hrs of the current day, Ah. mavi: probably only applies to controller; will be 0 for inverter.
 * @param dischargingAh Discharging amp-hrs of the current day, Ah. mavi: probably only applies to inverter; will be 0 for controller.
 * @param powerGenerationWh Power generation of the current day, Wh. Probably only applies to controller.
 * @param powerConsumptionWh Power consumption of the current day, Wh. Probably only applies to inverter.
 */
@Serializable
data class DailyStats(
    val batteryMinVoltage: Float,
    val batteryMaxVoltage: Float,
    val maxChargingCurrent: Float,
    val maxDischargingCurrent: Float,
    val maxChargingPower: UShort,
    val maxDischargingPower: UShort,
    val chargingAh: UShort,
    val dischargingAh: UShort,
    val powerGenerationWh: UShort,
    val powerConsumptionWh: UShort
) {
    override fun toString(): String =
        "DailyStats(batteryMinVoltage=$batteryMinVoltage V, batteryMaxVoltage=$batteryMaxVoltage V, maxChargingCurrent=$maxChargingCurrent A, maxDischargingCurrent=$maxDischargingCurrent A, maxChargingPower=$maxChargingPower W, maxDischargingPower=$maxDischargingPower W, chargingAmpHours=$chargingAh AH, dischargingAmpHours=$dischargingAh AH, powerGeneration=$powerGenerationWh WH, powerConsumption=$powerConsumptionWh WH)"
}

/**
 * @param batterySOC Current battery capacity value (state of charge), 0..100%
 * @param batteryVoltage battery voltage in V. Precision: 1 decimal points.
 * @param chargingCurrentToBattery charging current (to battery), A. Precision: 2 decimal points.
 * @param batteryTemp battery temperature in °C
 * @param controllerTemp controller temperature in °C
 * @param loadVoltage Street light (load) voltage in V. Precision: 1 decimal points.
 * @param loadCurrent Street light (load) current in A. Precision: 2 decimal points.
 * @param loadPower Street light (load) power, in W
 * @param solarPanelVoltage solar panel voltage, in V. Precision: 1 decimal points.
 * @param solarPanelCurrent Solar panel current (to controller), in A. Precision: 2 decimal points.
 * @param solarPanelPower charging power, in W
 */
@Serializable
data class PowerStatus(
    val batterySOC: UShort,
    val batteryVoltage: Float,
    val chargingCurrentToBattery: Float,
    val batteryTemp: Int,
    val controllerTemp: Int,
    val loadVoltage: Float,
    val loadCurrent: Float,
    val loadPower: UShort,
    val solarPanelVoltage: Float,
    val solarPanelCurrent: Float,
    val solarPanelPower: UShort
) {
    override fun toString(): String {
        return "PowerStatus(batterySOC=$batterySOC%, batteryVoltage=$batteryVoltage V, chargingCurrentToBattery=$chargingCurrentToBattery A, batteryTemp=$batteryTemp°C, controllerTemp=$controllerTemp°C, loadVoltage=$loadVoltage V, loadCurrent=$loadCurrent A, loadPower=$loadPower W, solarPanelVoltage=$solarPanelVoltage V, solarPanelCurrent=$solarPanelCurrent A, solarPanelPower=$solarPanelPower W)"
    }
}

/**
 * The static system information: hw/sw version, specs etc.
 * @property maxVoltage max. voltage supported by the system: 12V/24V/36V/48V/96V; 0xFF=automatic recognition of system voltage
 * @property ratedChargingCurrent rated charging current in A: 10A/20A/30A/45A/60A
 * @property ratedDischargingCurrent rated discharging current, 10A/20A/30A/45A/60A
 * @property productType product type
 * @property productModel the controller's model
 * @property softwareVersion Vmajor.minor.bugfix
 * @property hardwareVersion Vmajor.minor.bugfix
 * @property serialNumber serial number, 4 bytes formatted as a hex string, e.g. `1501FFFF`,
 * indicating it's the 65535th (hexadecimal FFFFH) unit produced in Jan. of 2015.
 */
@Serializable
data class SystemInfo(
    val maxVoltage: Int,
    val ratedChargingCurrent: Int,
    val ratedDischargingCurrent: Int,
    val productType: ProductType?,
    val productModel: String,
    val softwareVersion: String,
    val hardwareVersion: String,
    val serialNumber: String
) {
    override fun toString(): String {
        return "SystemInfo(maxVoltage=$maxVoltage V, ratedChargingCurrent=$ratedChargingCurrent A, ratedDischargingCurrent=$ratedDischargingCurrent A, productType=$productType, productModel=$productModel, softwareVersion=$softwareVersion, hardwareVersion=$hardwareVersion, serialNumber=$serialNumber)"
    }
}

enum class ProductType(val modbusValue: Byte) {
    Controller(0),
    Inverter(1),
    ;
}

/**
 * Thrown when Renogy returns a failure.
 * @param code the error code as received from Renogy. See [fromCode] for a list of
 * defined error codes. May be null if thrown because the response was mangled.
 */
class RenogyException(message: String, val code: Byte? = null) : Exception(message) {
    companion object {
        fun fromCode(code: Byte): RenogyException {
            val message = when(code) {
                0x01.toByte() -> "Function code not supported"
                0x02.toByte() -> "PDU start address is not correct or PDU start address + data length"
                0x03.toByte() -> "Data length in reading or writing register is too large"
                0x04.toByte() -> "Client fails to read or write register"
                0x05.toByte() -> "Data check code sent by server is not correct"
                else -> "Unknown"
            }
            return RenogyException("0x${code.toString(16)}: $message", code)
        }
    }
}

/**
 * Contains all data which can be pulled from the Renogy device.
 */
@Serializable
data class RenogyData(
    val systemInfo: SystemInfo,
    val powerStatus: PowerStatus,
    val dailyStats: DailyStats,
    val historicalData: HistoricalData,
    val status: RenogyStatus
) {
    fun toJson(prettyPrint: Boolean = true): String =
        utils.toJson(this, prettyPrint)
}
