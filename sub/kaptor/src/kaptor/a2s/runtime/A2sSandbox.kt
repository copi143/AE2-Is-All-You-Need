package kaptor.a2s.runtime

/**
 * a2s 运行时沙盒。由 [A2sEngine.dispatch] 绑定到当前线程，
 * 编译器在循环/调用入口注入 [tick] / [tickLoop] / [enterCall]。
 * 未绑定时所有检查为空操作，便于 JIT 单测直接调方法。
 */
class A2sSandbox(
    var instructionLimit: Int = 100_000,
    var loopIterationLimit: Int = 10_000,
    var recursionLimit: Int = 64,
    var timeLimitMs: Long = 0,
) {
    internal var instructions = 0
    internal var loops = 0
    internal var recursion = 0
    internal var deadlineNs = 0L

    fun reset() {
        instructions = 0
        loops = 0
        recursion = 0
        deadlineNs = if (timeLimitMs > 0) System.nanoTime() + timeLimitMs * 1_000_000L else 0L
    }

    companion object {
        private val current = ThreadLocal<A2sSandbox?>()

        fun enter(sandbox: A2sSandbox) {
            sandbox.reset()
            current.set(sandbox)
        }

        fun leave() {
            current.remove()
        }

        @JvmStatic
        fun tick() {
            val s = current.get() ?: return
            s.instructions++
            if (s.instructions > s.instructionLimit) {
                throw A2sLimitException("instruction limit ${s.instructionLimit}")
            }
            if (s.deadlineNs != 0L && (s.instructions and 31) == 0 && System.nanoTime() > s.deadlineNs) {
                throw A2sLimitException("time limit ${s.timeLimitMs}ms")
            }
        }

        @JvmStatic
        fun tickLoop() {
            val s = current.get() ?: return
            tick()
            s.loops++
            if (s.loops > s.loopIterationLimit) {
                throw A2sLimitException("loop iteration limit ${s.loopIterationLimit}")
            }
        }

        @JvmStatic
        fun enterCall() {
            val s = current.get() ?: return
            tick()
            s.recursion++
            if (s.recursion > s.recursionLimit) {
                throw A2sLimitException("recursion limit ${s.recursionLimit}")
            }
        }

        @JvmStatic
        fun exitCall() {
            val s = current.get() ?: return
            if (s.recursion > 0) s.recursion--
        }
    }
}

class A2sLimitException(message: String) : RuntimeException("a2s sandbox: $message")
