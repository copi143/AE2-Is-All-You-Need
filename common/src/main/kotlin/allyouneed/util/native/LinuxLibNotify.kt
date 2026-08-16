package allyouneed.util.native

import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.system.SharedLibrary

internal object LinuxLibNotify {
    @Volatile
    private var ready = false

    @Volatile
    private var failed = false

    private var notifyLib: SharedLibrary? = null
    private var gobjectLib: SharedLibrary? = null
    private var notifyInit = NULL
    private var notifyNew = NULL
    private var notifyShow = NULL
    private var gObjectUnref = NULL

    fun send(title: String, body: String): Boolean {
        if (!ensure()) return false
        MemoryStack.stackPush().use { stack ->
            val notification = JNI.invokePPPP(
                memAddress(stack.UTF8(title)),
                memAddress(stack.UTF8(body)),
                NULL,
                notifyNew,
            )
            if (notification == NULL) return false
            val ok = JNI.invokePPI(notification, NULL, notifyShow) != 0
            if (gObjectUnref != NULL) JNI.invokePV(notification, gObjectUnref)
            return ok
        }
    }

    fun resetForTest() {
        ready = false
        failed = false
        notifyLib = null
        gobjectLib = null
        notifyInit = NULL
        notifyNew = NULL
        notifyShow = NULL
        gObjectUnref = NULL
    }

    private fun ensure(): Boolean {
        if (ready) return true
        synchronized(this) {
            if (ready) return true
            if (failed) return false
            try {
                val notify = loadNativeLibrary("libnotify.so.4", "libnotify.so", "notify")
                if (notify == null) {
                    if (NativeNotify.lastError == null) {
                        NativeNotify.lastError = IllegalStateException("libnotify not found")
                    }
                    failed = true
                    return false
                }
                notifyInit = notify.getFunctionAddress("notify_init")
                notifyNew = notify.getFunctionAddress("notify_notification_new")
                notifyShow = notify.getFunctionAddress("notify_notification_show")
                if (notifyInit == NULL || notifyNew == NULL || notifyShow == NULL) {
                    failed = true
                    return false
                }
                val gobject = loadNativeLibrary("libgobject-2.0.so.0", "libgobject-2.0.so", "gobject-2.0")
                gObjectUnref = gobject?.getFunctionAddress("g_object_unref") ?: NULL
                val inited = MemoryStack.stackPush().use { stack ->
                    JNI.invokePI(memAddress(stack.UTF8("AE2 Is All You Need")), notifyInit) != 0
                }
                if (!inited) {
                    failed = true
                    return false
                }
                notifyLib = notify
                gobjectLib = gobject
                ready = true
                return true
            } catch (_: Throwable) {
                failed = true
                return false
            }
        }
    }
}
