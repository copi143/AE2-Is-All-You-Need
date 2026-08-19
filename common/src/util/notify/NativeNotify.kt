package allyouneed.util.notify

import org.lwjgl.system.APIUtil
import org.lwjgl.system.SharedLibrary

internal object NativeNotify {
    @Volatile
    private var failed = false

    @Volatile
    internal var lastError: Throwable? = null

    fun send(title: String, body: String): Boolean {
        if (failed) return false
        return try {
            val ok = when (OsKind.detect()) {
                OsKind.Linux -> LinuxLibNotify.send(title, body)
                OsKind.Mac -> MacOsNotify.send(title, body)
                OsKind.Windows -> WindowsShellNotify.send(title, body)
            }
            if (!ok) failed = true
            ok
        } catch (t: Throwable) {
            lastError = t
            failed = true
            false
        }
    }

    internal fun resetForTest() {
        failed = false
        lastError = null
        LinuxLibNotify.resetForTest()
    }
}

internal fun tryNativeNotify(title: String, body: String): Boolean = NativeNotify.send(title, body)

internal fun loadNativeLibrary(vararg names: String): SharedLibrary? {
    val candidates = LinkedHashSet<String>()
    for (name in names) {
        candidates += name
        if (name.startsWith("lib") && !name.startsWith("/")) {
            candidates += "/usr/lib/x86_64-linux-gnu/$name"
            candidates += "/usr/lib64/$name"
            candidates += "/usr/lib/$name"
            candidates += "/lib/x86_64-linux-gnu/$name"
        }
    }
    var last: Throwable? = null
    for (name in candidates) {
        try {
            return APIUtil.apiCreateLibrary(name)
        } catch (t: Throwable) {
            last = t
        }
    }
    if (last != null) NativeNotify.lastError = last
    return null
}
