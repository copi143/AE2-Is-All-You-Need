package kaptor

interface ScriptLogger {
    fun info(msg: String)
    fun warn(msg: String)
    fun error(msg: String, t: Throwable? = null)
    fun debug(msg: String)
}

internal object JULLogger : ScriptLogger {
    private val log = java.util.logging.Logger.getLogger("kaptor")
    override fun info(msg: String) = log.info(msg)
    override fun warn(msg: String) = log.warning(msg)
    override fun error(msg: String, t: Throwable?) {
        if (t != null) log.log(java.util.logging.Level.SEVERE, msg, t) else log.severe(msg)
    }
    override fun debug(msg: String) = log.fine(msg)
}

internal class SLF4JScriptLogger : ScriptLogger {
    private val log = org.slf4j.LoggerFactory.getLogger("kaptor")
    override fun info(msg: String) = log.info(msg)
    override fun warn(msg: String) = log.warn(msg)
    override fun error(msg: String, t: Throwable?) = if (t != null) log.error(msg, t) else log.error(msg)
    override fun debug(msg: String) = log.debug(msg)
}

@PublishedApi
internal fun createLogger(): ScriptLogger = try {
    Class.forName("org.slf4j.LoggerFactory")
    SLF4JScriptLogger()
} catch (_: ClassNotFoundException) {
    JULLogger
}
