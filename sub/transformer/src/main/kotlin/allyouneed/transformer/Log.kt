package allyouneed.transformer

object Log {
    private const val NAME = "AE2IsAllYouNeed/Core"

    fun debug(pattern: String, vararg args: Any?) = emit("debug", format(pattern, args), null)

    fun info(pattern: String, vararg args: Any?) = emit("info", format(pattern, args), null)

    fun warn(pattern: String, vararg args: Any?) = emit("warn", format(pattern, args), null)

    fun error(pattern: String, thrown: Throwable? = null, vararg args: Any?) =
        emit("error", format(pattern, args), thrown)

    private fun format(pattern: String, args: Array<out Any?>): String {
        if (args.isEmpty()) return pattern
        val sb = StringBuilder(pattern.length + args.size * 16)
        var i = 0
        var p = 0
        while (true) {
            val at = pattern.indexOf("{}", p)
            if (at < 0 || i >= args.size) {
                sb.append(pattern, p, pattern.length)
                break
            }
            sb.append(pattern, p, at).append(args[i])
            i++
            p = at + 2
        }
        return sb.toString()
    }

    private fun emit(level: String, message: String, thrown: Throwable?) {
        try {
            val logger = Class.forName("org.slf4j.LoggerFactory")
                .getMethod("getLogger", String::class.java)
                .invoke(null, NAME)
            if (thrown != null) {
                logger.javaClass.getMethod(level, String::class.java, Throwable::class.java)
                    .invoke(logger, message, thrown)
            } else {
                logger.javaClass.getMethod(level, String::class.java).invoke(logger, message)
            }
        } catch (_: Throwable) {
            val out = if (level == "error") System.err else System.out
            out.println("[$NAME] ${level.uppercase()}: $message")
            thrown?.printStackTrace(out)
        }
    }
}
