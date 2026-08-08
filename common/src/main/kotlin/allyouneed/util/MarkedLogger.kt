package allyouneed.util

import org.slf4j.Logger
import org.slf4j.Marker

@Suppress("unused", "JavaDefaultMethodsNotOverriddenByDelegation")
class MarkedLogger(private val target: Logger, private val attached: Marker) : Logger by target {
    override fun isTraceEnabled(): Boolean = target.isTraceEnabled(attached)
    override fun trace(msg: String) = target.trace(attached, msg)
    override fun trace(format: String, arg: Any?) = target.trace(attached, format, arg)
    override fun trace(format: String, arg1: Any?, arg2: Any?) = target.trace(attached, format, arg1, arg2)
    override fun trace(format: String, vararg arguments: Any?) = target.trace(attached, format, *arguments)
    override fun trace(msg: String, t: Throwable) = target.trace(attached, msg, t)

    override fun isDebugEnabled(): Boolean = target.isDebugEnabled(attached)
    override fun debug(msg: String) = target.debug(attached, msg)
    override fun debug(format: String, arg: Any?) = target.debug(attached, format, arg)
    override fun debug(format: String, arg1: Any?, arg2: Any?) = target.debug(attached, format, arg1, arg2)
    override fun debug(format: String, vararg arguments: Any?) = target.debug(attached, format, *arguments)
    override fun debug(msg: String, t: Throwable) = target.debug(attached, msg, t)

    override fun isInfoEnabled(): Boolean = target.isInfoEnabled(attached)
    override fun info(msg: String) = target.info(attached, msg)
    override fun info(format: String, arg: Any?) = target.info(attached, format, arg)
    override fun info(format: String, arg1: Any?, arg2: Any?) = target.info(attached, format, arg1, arg2)
    override fun info(format: String, vararg arguments: Any?) = target.info(attached, format, *arguments)
    override fun info(msg: String, t: Throwable) = target.info(attached, msg, t)

    override fun isWarnEnabled(): Boolean = target.isWarnEnabled(attached)
    override fun warn(msg: String) = target.warn(attached, msg)
    override fun warn(format: String, arg: Any?) = target.warn(attached, format, arg)
    override fun warn(format: String, arg1: Any?, arg2: Any?) = target.warn(attached, format, arg1, arg2)
    override fun warn(format: String, vararg arguments: Any?) = target.warn(attached, format, *arguments)
    override fun warn(msg: String, t: Throwable) = target.warn(attached, msg, t)

    override fun isErrorEnabled(): Boolean = target.isErrorEnabled(attached)
    override fun error(msg: String) = target.error(attached, msg)
    override fun error(format: String, arg: Any?) = target.error(attached, format, arg)
    override fun error(format: String, arg1: Any?, arg2: Any?) = target.error(attached, format, arg1, arg2)
    override fun error(format: String, vararg arguments: Any?) = target.error(attached, format, *arguments)
    override fun error(msg: String, t: Throwable) = target.error(attached, msg, t)
}
