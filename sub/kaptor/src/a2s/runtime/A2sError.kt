package kaptor.a2s.runtime

/**
 * 引擎错误记录。包含发生阶段、来源脚本、异常信息。
 */
data class A2sError(
    val timestamp: Long = System.currentTimeMillis(),
    val scriptName: String?,
    val phase: Phase,
    val message: String,
    val cause: Throwable? = null,
) {
    enum class Phase { PARSE, COMPILE, LOAD, RUNTIME }
}
