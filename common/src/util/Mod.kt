@file:Suppress("SpellCheckingInspection")

package allyouneed.util

import allyouneed.Platform
import allyouneed.util.MarkedLogger.Companion.marked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger

const val MODID = "ae2isallyouneed"
const val MODNAME = "AE2 Is All You Need"

@JvmField
val LOGNAME = MODNAME.replace(" ", "")

@JvmField
val logger: Logger = LoggerFactory.getLogger(LOGNAME)

@JvmField
val coreLogger: Logger = logger.marked("Core")

@JvmField
val debugLogger: Logger = if (Platform.isDev) logger.marked("Debug") else NOPLogger.NOP_LOGGER
