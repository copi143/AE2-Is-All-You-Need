package allyouneed.util.notify

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

/**
 * 跨平台桌面通知。仅在客户端已注册 [focusProbe] 且游戏窗口失焦时发送。
 * 系统通知工具缺失或全部失败时静默 no-op。
 */
object DesktopNotify {
    fun interface FocusProbe {
        fun isWindowActive(): Boolean
    }

    @Volatile
    var focusProbe: FocusProbe? = null

    @Volatile
    internal var backend: OsNotifyBackend = defaultNotifyBackend()

    @Volatile
    internal var enqueue: (Runnable) -> Unit = DEFAULT_ENQUEUE

    /**
     * 异步发送。窗口聚焦、未初始化或无后端时不发送。
     */
    fun send(title: String, body: String) {
        if (focusProbe?.isWindowActive() != false) return
        val t = sanitizeNotifyText(title, NOTIFY_TITLE_MAX)
        val b = sanitizeNotifyText(body, NOTIFY_BODY_MAX)
        if (t.isEmpty() && b.isEmpty()) return
        enqueue {
            try {
                if (!backend.send(t, b) && backend.isUnavailable) {
                    warnUnavailable()
                }
            } catch (e: Throwable) {
                log.debug("desktop notify failed", e)
            }
        }
    }

    /**
     * 同步发送并忽略窗口焦点。用于测试与调试。
     * @return 是否有系统通知后端接受了该消息
     */
    fun sendBlocking(title: String, body: String): Boolean {
        val t = sanitizeNotifyText(title, NOTIFY_TITLE_MAX)
        val b = sanitizeNotifyText(body, NOTIFY_BODY_MAX)
        if (t.isEmpty() && b.isEmpty()) return false
        return try {
            val ok = backend.send(t, b)
            if (!ok && backend.isUnavailable) warnUnavailable()
            ok
        } catch (e: Throwable) {
            log.debug("desktop notify failed", e)
            false
        }
    }

    internal fun resetForTest() {
        focusProbe = null
        backend = OsNotifyBackend()
        enqueue = DEFAULT_ENQUEUE
        warned = false
        NativeNotify.resetForTest()
    }

    @Volatile
    private var warned = false

    private fun warnUnavailable() {
        if (warned) return
        warned = true
        log.warn("No desktop notification backend available on this system")
    }
}

internal fun defaultNotifyBackend() = OsNotifyBackend(nativeSend = ::tryNativeNotify)

private val log = LoggerFactory.getLogger("AE2IsAllYouNeed")

private val DEFAULT_ENQUEUE: (Runnable) -> Unit = { task ->
    NOTIFY_EXECUTOR.execute(task)
}

private val NOTIFY_EXECUTOR = Executors.newSingleThreadExecutor { r ->
    Thread(r, "ayn-notify").apply { isDaemon = true }
}
