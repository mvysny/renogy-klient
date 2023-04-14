package utils

import kotlin.random.Random
import kotlin.random.nextUInt

fun Random.nextFloat(from: Float, to: Float): Float =
    nextDouble(from.toDouble(), to.toDouble()).toFloat()

fun Random.nextUShort(from: UShort, to: UShort): UShort =
    nextUInt(from.toUInt(), to.toUInt()).toUShort()
