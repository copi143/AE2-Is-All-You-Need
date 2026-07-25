package allyouneed.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("SpellCheckingInspection")
const val MODID = "ae2isallyouneed"

@Suppress("SpellCheckingInspection")
const val MODNAME = "AE2 Is All You Need"

@JvmField
val logger: Logger = LoggerFactory.getLogger(MODNAME)

val Double.Ki get() = this * 1024.0
val Double.Mi get() = this * 1024.0 * 1024.0
val Double.Gi get() = this * 1024.0 * 1024.0 * 1024.0
val Double.Ti get() = this * 1024.0 * 1024.0 * 1024.0 * 1024.0
val Double.Pi get() = this * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0
val Double.Ei get() = this * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0
