package kaptor.runtime

class ScriptSandbox(var limit: Int) {
    private var counter = 0

    fun reset(newLimit: Int = limit) {
        counter = 0
        limit = newLimit
    }

    fun consume(amount: Int): Boolean {
        counter += amount
        return counter <= limit
    }

    fun check(): Boolean = counter <= limit

    fun getCounter(): Int = counter

    fun getRemaining(): Int = maxOf(0, limit - counter)

    companion object {
        @JvmStatic
        fun throwLimitExceeded(message: String) {
            throw ScriptLimitException(message)
        }
    }
}

class ScriptLimitException(message: String) : RuntimeException("Script instruction limit exceeded: $message")
