package utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val prettyPrintJson = Json { prettyPrint = true }

inline fun <reified T> toJson(value: T, prettyPrint: Boolean): String {
    val json: Json = if (prettyPrint) prettyPrintJson else Json
    return json.encodeToString(value)
}
