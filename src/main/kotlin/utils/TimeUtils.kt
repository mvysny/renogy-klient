package utils

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

operator fun Instant.minus(other: Instant): Duration = (this.toEpochMilli() - other.toEpochMilli()).milliseconds
