package datalogger.influxdb

import kotlinx.serialization.Serializable

/**
 * See https://docs.influxdata.com/influxdb/v2.7/write-data/delete-data/
 * @property start e.g. `2020-03-01T00:00:00Z`
 * @property stop e.g. `2020-03-01T00:00:00Z`
 * @property predicate e.g. `_measurement="example-measurement" AND tag="foo"`
 */
@Serializable
data class InfluxDBDeleteRequest(
    val start: String,
    val stop: String,
    val predicate: String? = null
)
