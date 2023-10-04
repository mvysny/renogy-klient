package clients

import Log
import com.github.mvysny.unsigned.*
import utils.*
import kotlin.time.Duration

/**
 * Communicates with Renogy Rover over [io]. Doesn't close [io] on [close].
 * @param deviceAddress identifies the Renogy Rover if there are multiple Renogy devices on the network.
 * @param timeout read/write timeout.
 */
class RenogyModbusClient(val io: IO, val timeout: Duration, val deviceAddress: Byte = 0x01) : RenogyClient {
    init {
        require(deviceAddress in 0..0xf7) { "$deviceAddress: Device address must be 0x01..0xf7, 0x00 is a broadcast address to which all slaves respond but do not return commands" }
    }

    /**
     * Performs the ReadRegister call and returns the data returned.
     *
     * Internal, don't use. Visible for testing only.
     */
    fun readRegister(startAddress: Int, noOfReadBytes: Int): ByteArray {
        require(startAddress in 0..0x1000) { "$startAddress: must be 0..0x1000" }
        val noOfReadWords: Int = noOfReadBytes / 2
        require(noOfReadWords in 0x1..0x7D) { "$noOfReadWords: must be 0x0001..0x007D" }

        // prepare request
        val request = ByteArray(8)
        request[0] = deviceAddress
        request[1] = COMMAND_READ_REGISTER
        request.setShort(2, startAddress)
        request.setShort(4, noOfReadWords)
        val crc = CRC16Modbus()
        crc.update(request, 0, 6)
        request.setUShort(6, crc.crc, Endian.Little) // for CRC, low byte is sent first, then the high byte.
        io.write(request, timeout)

        // read response
        val responseHeader = io.read(3, timeout)
        if (responseHeader[0] != deviceAddress) {
            throw RenogyException("${startAddress.toString(16)}: Invalid response: expected deviceAddress $deviceAddress but got ${responseHeader[0]}")
        }
        if (responseHeader[1] == 0x83.toByte()) {
            // error response. First verify checksum.
            verifyCRC(crcOf(responseHeader), io.read(2))
            throw RenogyException.fromCode(responseHeader[2])
        }
        if (responseHeader[1] != 0x03.toByte()) {
            throw RenogyException("${startAddress.toString(16)}: Unexpected response code: expected 3 but got ${responseHeader[1]}")
        }
        // normal response. Read the data.
        val dataLength = responseHeader[2].toInt()
        require(dataLength == noOfReadBytes) { "${startAddress.toString(16)}: the call was expected to return $noOfReadBytes bytes but got $dataLength" }

        if (dataLength !in 1..0xFA) {
            throw RenogyException("${startAddress.toString(16)}: dataLength must be 0x01..0xFA but was $dataLength")
        }
        val data = io.read(dataLength)
        // verify the CRC
        verifyCRC(crcOf(responseHeader, data), io.read(2))

        // all OK. Return the response
        return data
    }

    private fun verifyCRC(expected: UShort, actual: ByteArray) {
        require(actual.size == 2) { "${actual.toHex()}: must be of size 2" }
        // for CRC, low byte is sent first, then the high byte.
        val actualUShort: UShort = actual.getUShort(0, Endian.Little)
        if (actualUShort != expected) {
            throw RenogyException("Checksum mismatch: expected ${expected.toString(16)} but got ${actualUShort.toString(16)}")
        }
    }

    /**
     * Retrieves the [SystemInfo] from the device.
     */
    override fun getSystemInfo(): SystemInfo {
        log.debug("getting system info")
        var result = readRegister(0x0A, 4)
        val maxVoltage = result[0].toInt()
        val ratedChargingCurrent = result[1].toInt()
        val ratedDischargingCurrent = result[2].toInt()
        val productTypeNum = result[3]
        val productType = ProductType.values().firstOrNull { it.modbusValue == productTypeNum }

        result = readRegister(0x0C, 16)
        val productModel = result.toAsciiString().trim()

        // software version/hardware version
        result = readRegister(0x0014, 8)
        val softvareVersion = "V${result[1]}.${result[2]}.${result[3]}"
        val hardwareVersion = "V${result[5]}.${result[6]}.${result[7]}"

        // serial number
        result = readRegister(0x0018, 4)
        val serialNumber = result.toHex()

        return SystemInfo(maxVoltage,ratedChargingCurrent, ratedDischargingCurrent, productType, productModel, softvareVersion, hardwareVersion, serialNumber)
    }

    /**
     * Retrieves the current status of the device, e.g. current voltage on
     * the solar panels.
     */
    fun getPowerStatus(): PowerStatus {
        log.debug("getting power status")
        val result = readRegister(0x0100, 20)
        val batterySOC = result.getUShort(0)
        val batteryVoltage = result.getUShort(2).toFloat() / 10
        // @todo in modbus spec examples, there's no chargingCurrentToBattery, and
        // batteryTemp is a WORD at 0x102 and controllerTemp is a WORD at 0x103 - check!
        val chargingCurrentToBattery = result.getUShort(4).toFloat() / 100
        val batteryTemp = result[7].toInt()
        val controllerTemp = result[6].toInt()
        val loadVoltage = result.getUShort(8).toFloat() / 10
        val loadCurrent = result.getUShort(10).toFloat() / 100
        val loadPower = result.getUShort(12)
        val solarPanelVoltage = result.getUShort(14).toFloat() / 10
        val solarPanelCurrent = result.getUShort(16).toFloat() / 100
        val solarPanelPower = result.getUShort(18)
        return PowerStatus(batterySOC, batteryVoltage, chargingCurrentToBattery, batteryTemp, controllerTemp, loadVoltage, loadCurrent, loadPower, solarPanelVoltage, solarPanelCurrent, solarPanelPower)
    }

    /**
     * Returns the daily statistics.
     */
    fun getDailyStats(): DailyStats {
        log.debug("getting daily stats")
        val result = readRegister(0x010B, 20)
        val batteryMinVoltage: Float = result.getUShort(0).toFloat() / 10
        val batteryMaxVoltage: Float = result.getUShort(2).toFloat() / 10
        val maxChargingCurrent: Float = result.getUShort(4).toFloat() / 100
        val maxDischargingCurrent: Float = result.getUShort(6).toFloat() / 100
        val maxChargingPower: UShort = result.getUShort(8)
        val maxDischargingPower: UShort = result.getUShort(10)
        val chargingAmpHours: UShort = result.getUShort(12)
        val dischargingAmpHours: UShort = result.getUShort(14)
        // The manual says kWh/10000, however that value does not correspond to chargingAmpHours: chargingAmpHours=2 but this value is 5 for 24V system.
        // The example in manual says kWh which would simply be too much.
        // I'll make an educated guess here: it's Wh.
        val powerGenerationWh: UShort = result.getUShort(16)
        val powerConsumptionWh: UShort = result.getUShort(18)
        return DailyStats(batteryMinVoltage, batteryMaxVoltage, maxChargingCurrent, maxDischargingCurrent, maxChargingPower, maxDischargingPower, chargingAmpHours, dischargingAmpHours, powerGenerationWh, powerConsumptionWh)
    }

    /**
     * Returns the historical data summary.
     */
    fun getHistoricalData(): HistoricalData {
        log.debug("getting historical data")
        val result = readRegister(0x0115, 22)
        val daysUp: UShort = result.getUShort(0)
        val batteryOverDischargeCount: UShort = result.getUShort(2)
        val batteryFullChargeCount: UShort = result.getUShort(4)
        val totalChargingBatteryAH: UInt = result.getUInt(6)
        val totalDischargingBatteryAH: UInt = result.getUInt(10)
        // The manual spec says kWh/10000; the manual example says kWh which doesn't make any sense.
        // I'll make an educated guess here: it's Wh.
        val cumulativePowerGenerationWH: UInt = result.getUInt(14)
        val cumulativePowerConsumptionWH: UInt = result.getUInt(18)
        return HistoricalData(daysUp, batteryOverDischargeCount, batteryFullChargeCount, totalChargingBatteryAH, totalDischargingBatteryAH, cumulativePowerGenerationWH, cumulativePowerConsumptionWH)
    }

    /**
     * Returns the current charging status and any current faults.
     */
    fun getStatus(): RenogyStatus {
        log.debug("getting status")
        val result = readRegister(0x120, 6)
        val streetLightOn = (result[0].toUByte() and 0x80.toUByte()) != 0.toUByte()
        val streetLightBrightness = result[0].toUByte() and 0x7Fu
        val chargingState = ChargingState.fromModbus(result[1].toUByte())
        val faultBits = result.getUInt(2)
        val faults = ControllerFaults.fromModbus(faultBits)
        return RenogyStatus(streetLightOn, streetLightBrightness, chargingState, faults)
    }

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData = RenogyData(
        cachedSystemInfo ?: getSystemInfo(),
        getPowerStatus(),
        getDailyStats(),
        getHistoricalData(),
        getStatus()
    )

    override fun toString(): String =
        "RenogyModbusClient(io=$io, deviceAddress=$deviceAddress)"

    /**
     * Does nothing; doesn't even close the [io].
     */
    override fun close() {}

    companion object {
        private val COMMAND_READ_REGISTER: Byte = 0x03
        private val log = Log<RenogyModbusClient>()
    }
}
