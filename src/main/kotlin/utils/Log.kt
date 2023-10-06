package utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Simple logger, allows for programmatic configuration of whether debug logs are printed or not.
 * Logs to [logger].
 */
class Log(private val logger: Logger) {
    constructor(name: String) : this(LoggerFactory.getLogger(name))
    constructor(javaClass: Class<*>) : this(LoggerFactory.getLogger(javaClass))

    fun debug(msg: String, t: Throwable? = null) {
        if (isDebugEnabled) {
            logger.debug(msg, t)
        }
    }
    fun info(msg: String, t: Throwable? = null) {
        logger.info(msg, t)
    }
    fun warn(msg: String, t: Throwable? = null) {
        logger.warn(msg, t)
    }
    fun error(msg: String, t: Throwable? = null) {
        logger.error(msg, t)
    }
    companion object {
        /**
         * If true, [debug] messages are logged as well. Defaults to false.
         */
        var isDebugEnabled = false
    }
}

inline fun <reified C> Log(): Log = Log(C::class.java)
