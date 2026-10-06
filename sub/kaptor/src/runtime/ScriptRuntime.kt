package kaptor.runtime

object ScriptRuntime {
    private val sandbox = ThreadLocal.withInitial { ScriptSandbox(1000) }

    fun currentSandbox(): ScriptSandbox = sandbox.get()

    fun setSandboxLimit(limit: Int) {
        sandbox.get().reset(limit)
    }

    fun add(a: Any?, b: Any?): Any? {
        return when {
            a is String || b is String -> "${a ?: "null"}${b ?: "null"}"
            a is Long && b is Long -> a + b
            a is Int && b is Int -> a + b
            a is Double && b is Double -> a + b
            a is Float && b is Float -> a + b
            a is Long && b is Int -> a + b.toLong()
            a is Int && b is Long -> a.toLong() + b
            a is Number && b is Number -> a.toDouble() + b.toDouble()
            else -> "${a ?: "null"}${b ?: "null"}"
        }
    }

    fun sub(a: Any?, b: Any?): Any? {
        return when {
            a is Long && b is Long -> a - b
            a is Int && b is Int -> a - b
            a is Double && b is Double -> a - b
            a is Float && b is Float -> a - b
            a is Long && b is Int -> a - b.toLong()
            a is Int && b is Long -> a.toLong() - b
            a is Number && b is Number -> a.toDouble() - b.toDouble()
            else -> throw ArithmeticException("Cannot subtract ${a?.javaClass?.name} and ${b?.javaClass?.name}")
        }
    }

    fun mul(a: Any?, b: Any?): Any? {
        return when {
            a is Long && b is Long -> a * b
            a is Int && b is Int -> a * b
            a is Double && b is Double -> a * b
            a is Float && b is Float -> a * b
            a is String && b is Number -> a.repeat(b.toInt())
            a is Number && b is String -> b.repeat(a.toInt())
            a is Long && b is Int -> a * b.toLong()
            a is Int && b is Long -> a.toLong() * b
            a is Number && b is Number -> a.toDouble() * b.toDouble()
            else -> throw ArithmeticException("Cannot multiply ${a?.javaClass?.name} and ${b?.javaClass?.name}")
        }
    }

    fun div(a: Any?, b: Any?): Any? {
        return when {
            b is Number && b.toDouble() == 0.0 -> throw ArithmeticException("Division by zero")
            a is Long && b is Long -> a / b
            a is Int && b is Int -> a / b
            a is Double && b is Double -> a / b
            a is Float && b is Float -> a / b
            a is Long && b is Int -> a / b.toLong()
            a is Int && b is Long -> a.toLong() / b
            a is Number && b is Number -> a.toDouble() / b.toDouble()
            else -> throw ArithmeticException("Cannot divide ${a?.javaClass?.name} and ${b?.javaClass?.name}")
        }
    }

    fun mod(a: Any?, b: Any?): Any? {
        return when {
            b is Number && b.toDouble() == 0.0 -> throw ArithmeticException("Modulo by zero")
            a is Long && b is Long -> a % b
            a is Int && b is Int -> a % b
            a is Double && b is Double -> a % b
            a is Float && b is Float -> a % b
            a is Long && b is Int -> a % b.toLong()
            a is Int && b is Long -> a.toLong() % b
            a is Number && b is Number -> a.toDouble() % b.toDouble()
            else -> throw ArithmeticException("Cannot modulo ${a?.javaClass?.name} and ${b?.javaClass?.name}")
        }
    }

    fun equals(a: Any?, b: Any?): Boolean = a == b

    fun notEquals(a: Any?, b: Any?): Boolean = a != b

    fun lessThan(a: Any?, b: Any?): Boolean {
        return when {
            a is Long && b is Long -> a < b
            a is Int && b is Int -> a < b
            a is Double && b is Double -> a < b
            a is Comparable<*> && b is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                (a as Comparable<Any>).compareTo(b) < 0
            }

            else -> false
        }
    }

    fun lessEqual(a: Any?, b: Any?): Boolean {
        return when {
            a is Long && b is Long -> a <= b
            a is Int && b is Int -> a <= b
            a is Double && b is Double -> a <= b
            a is Comparable<*> && b is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                (a as Comparable<Any>).compareTo(b) <= 0
            }

            else -> false
        }
    }

    fun greaterThan(a: Any?, b: Any?): Boolean {
        return when {
            a is Long && b is Long -> a > b
            a is Int && b is Int -> a > b
            a is Double && b is Double -> a > b
            a is Comparable<*> && b is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                (a as Comparable<Any>).compareTo(b) > 0
            }

            else -> false
        }
    }

    fun greaterEqual(a: Any?, b: Any?): Boolean {
        return when {
            a is Long && b is Long -> a >= b
            a is Int && b is Int -> a >= b
            a is Double && b is Double -> a >= b
            a is Comparable<*> && b is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                (a as Comparable<Any>).compareTo(b) >= 0
            }

            else -> false
        }
    }

    fun bitAnd(a: Any?, b: Any?): Any? {
        return when {
            a is Long && b is Long -> a and b
            a is Int && b is Int -> a and b
            a is Number && b is Number -> a.toLong() and b.toLong()
            else -> throw ArithmeticException("Cannot bit-and ${a?.javaClass?.name} and ${b?.javaClass?.name}")
        }
    }

    fun bitOr(a: Any?, b: Any?): Any? {
        return when {
            a is Long && b is Long -> a or b
            a is Int && b is Int -> a or b
            a is Number && b is Number -> a.toLong() or b.toLong()
            else -> throw ArithmeticException("Cannot bit-or ${a?.javaClass?.name} and ${b?.javaClass?.name}")
        }
    }

    fun bitXor(a: Any?, b: Any?): Any? {
        return when {
            a is Long && b is Long -> a xor b
            a is Int && b is Int -> a xor b
            a is Number && b is Number -> a.toLong() xor b.toLong()
            else -> throw ArithmeticException("Cannot bit-xor ${a?.javaClass?.name} and ${b?.javaClass?.name}")
        }
    }

    fun shl(a: Any?, b: Any?): Any? {
        return when {
            a is Long && b is Number -> a shl b.toInt()
            a is Int && b is Number -> a shl b.toInt()
            else -> throw ArithmeticException("Cannot shift-left ${a?.javaClass?.name}")
        }
    }

    fun shr(a: Any?, b: Any?): Any? {
        return when {
            a is Long && b is Number -> a shr b.toInt()
            a is Int && b is Number -> a shr b.toInt()
            else -> throw ArithmeticException("Cannot shift-right ${a?.javaClass?.name}")
        }
    }

    fun neg(a: Any?): Any? {
        return when (a) {
            is Long -> -a
            is Int -> -a
            is Double -> -a
            is Float -> -a
            is Number -> -a.toDouble()
            else -> throw ArithmeticException("Cannot negate ${a?.javaClass?.name}")
        }
    }

    fun not(a: Any?): Boolean {
        return when (a) {
            is Boolean -> !a
            is Number -> a.toDouble() == 0.0
            null -> true
            else -> false
        }
    }

    fun bitNot(a: Any?): Any? {
        return when (a) {
            is Long -> a.inv()
            is Int -> a.inv()
            is Number -> a.toLong().inv()
            else -> throw ArithmeticException("Cannot bit-not ${a?.javaClass?.name}")
        }
    }

    fun toBool(a: Any?): Boolean {
        return when (a) {
            is Boolean -> a
            is Number -> a.toDouble() != 0.0
            is String -> a.isNotEmpty()
            null -> false
            else -> true
        }
    }

    fun toString(a: Any?): String = a?.toString() ?: "null"

    fun toInt(a: Any?): Int {
        return when (a) {
            is Number -> a.toInt()
            is String -> a.toIntOrNull() ?: 0
            else -> 0
        }
    }

    fun toLong(a: Any?): Long {
        return when (a) {
            is Number -> a.toLong()
            is String -> a.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    fun toDouble(a: Any?): Double {
        return when (a) {
            is Number -> a.toDouble()
            is String -> a.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    fun len(a: Any?): Int {
        return when (a) {
            is String -> a.length
            is Collection<*> -> a.size
            is Array<*> -> a.size
            is Map<*, *> -> a.size
            else -> 0
        }
    }

    fun println(a: Any?) {
        kotlin.io.println(a?.toString() ?: "null")
    }
}
