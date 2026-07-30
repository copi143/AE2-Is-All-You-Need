package kaptor.runtime

open class ScriptBase {
    protected fun on(eventType: String, body: (Any?) -> Unit) {}

    protected fun before(eventType: String, body: (Any?) -> Unit) {}

    protected fun after(eventType: String, body: (Any?) -> Unit) {}
}
