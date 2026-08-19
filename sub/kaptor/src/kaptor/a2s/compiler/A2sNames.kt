package kaptor.a2s.compiler

/**
 * 编译产物命名约定。
 */
object A2sNames {
    const val GEN_PKG = "kaptor/a2s/gen"

    /** 事件类内部名：kaptor/a2s/gen/A2sEvent_MyEvent */
    fun eventClass(eventName: String): String = "$GEN_PKG/A2sEvent_${sanitize(eventName)}"

    /** 脚本类内部名：kaptor/a2s/gen/A2sScript_3 */
    fun scriptClass(index: Int): String = "$GEN_PKG/A2sScript_$index"

    fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9_]"), "_")
}
