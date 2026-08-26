package allyouneed.util.notify

import java.io.File
import java.util.concurrent.TimeUnit

internal const val NOTIFY_TITLE_MAX = 200
internal const val NOTIFY_BODY_MAX = 1000

internal enum class OsKind {
    Windows, Mac, Linux;

    companion object {
        fun detect(osName: String = System.getProperty("os.name", "")): OsKind {
            val n = osName.lowercase()
            return when {
                n.contains("win") -> Windows
                n.contains("mac") || n.contains("darwin") -> Mac
                else -> Linux
            }
        }
    }
}

internal data class NotifyCommand(
    val argv: List<String>,
    val env: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 3_000,
)

internal class NotifyTool(
    val name: String,
    val resolve: () -> String?,
    val build: (exe: String, title: String, body: String) -> NotifyCommand,
)

internal class OsNotifyBackend(
    private val os: OsKind = OsKind.detect(),
    private val resolve: (String) -> String? = { resolveExecutable(it) },
    private val runner: (NotifyCommand) -> Boolean = ::runNotifyCommand,
    private val nativeSend: ((String, String) -> Boolean)? = null,
) {
    private val lock = Any()

    @Volatile
    private var cached: NotifyTool? = null

    @Volatile
    private var unavailable = false

    @Volatile
    private var nativeFailed = false

    fun send(title: String, body: String): Boolean {
        synchronized(lock) {
            if (unavailable) return false
            if (!nativeFailed && nativeSend != null) {
                if (nativeSend.invoke(title, body)) return true
                nativeFailed = true
            }
            val tools = toolsFor(os, resolve)
            val ordered = cached?.let { listOf(it) + tools.filter { t -> t.name != it.name } } ?: tools
            for (tool in ordered) {
                val exe = tool.resolve() ?: continue
                if (runner(tool.build(exe, title, body))) {
                    cached = tool
                    return true
                }
            }
            unavailable = true
            return false
        }
    }

    val isUnavailable: Boolean get() = unavailable

    fun reset() {
        synchronized(lock) {
            cached = null
            unavailable = false
            nativeFailed = false
        }
    }
}

internal fun sanitizeNotifyText(text: String, max: Int): String {
    if (text.isEmpty() || max <= 0) return ""
    val out = StringBuilder(text.length.coerceAtMost(max))
    for (c in text) {
        if (c == '\u0000') continue
        if (out.length >= max) break
        out.append(c)
    }
    return out.toString().trim()
}

internal fun escapeAppleScript(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"")

internal fun resolveExecutable(
    nameOrPath: String,
    pathEnv: String? = System.getenv("PATH"),
    windows: Boolean = OsKind.detect() == OsKind.Windows,
): String? {
    if (nameOrPath.isEmpty()) return null
    val explicit = nameOrPath.contains('/') || nameOrPath.contains('\\')
    if (explicit) {
        val file = File(nameOrPath)
        return nameOrPath.takeIf { file.isFile && file.canExecute() }
    }
    val dirs = pathEnv?.split(File.pathSeparator).orEmpty()
    val extensions = if (windows) listOf("", ".exe", ".cmd", ".bat") else listOf("")
    for (dir in dirs) {
        if (dir.isEmpty()) continue
        for (ext in extensions) {
            val candidate = File(dir, nameOrPath + ext)
            if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
        }
    }
    return null
}

internal fun runNotifyCommand(cmd: NotifyCommand): Boolean {
    return try {
        val pb = ProcessBuilder(cmd.argv)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        if (cmd.env.isNotEmpty()) {
            val env = pb.environment()
            cmd.env.forEach { (k, v) -> env[k] = v }
        }
        val proc = pb.start()
        val done = proc.waitFor(cmd.timeoutMs, TimeUnit.MILLISECONDS)
        if (!done) {
            proc.destroyForcibly()
            return false
        }
        proc.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}

internal fun toolsFor(os: OsKind, resolve: (String) -> String?): List<NotifyTool> = when (os) {
    OsKind.Linux -> linuxTools(resolve)
    OsKind.Mac -> macTools(resolve)
    OsKind.Windows -> windowsTools(resolve)
}

internal fun linuxTools(resolve: (String) -> String?): List<NotifyTool> = listOf(
    NotifyTool("notify-send", { resolve("notify-send") }) { exe, title, body ->
        NotifyCommand(listOf(exe, "--app-name=AE2 Is All You Need", "--", title, body))
    },
    NotifyTool("gdbus", { resolve("gdbus") }) { exe, title, body ->
        NotifyCommand(
            listOf(
                exe, "call", "--session",
                "--dest", "org.freedesktop.Notifications",
                "--object-path", "/org/freedesktop/Notifications",
                "--method", "org.freedesktop.Notifications.Notify",
                "AE2 Is All You Need", "0", "", title, body, "[]", "{}", "5000",
            ),
        )
    },
    NotifyTool("zenity", { resolve("zenity") }) { exe, title, body ->
        val text = if (body.isEmpty()) title else "$title\n$body"
        NotifyCommand(listOf(exe, "--notification", "--text=$text"))
    },
    NotifyTool("kdialog", { resolve("kdialog") }) { exe, title, body ->
        NotifyCommand(listOf(exe, "--title", title, "--passivepopup", body.ifEmpty { title }, "5"))
    },
)

internal fun macTools(resolve: (String) -> String?): List<NotifyTool> = listOf(
    NotifyTool("osascript", { resolve("/usr/bin/osascript") ?: resolve("osascript") }) { exe, title, body ->
        val script = """display notification "${escapeAppleScript(body)}" with title "${escapeAppleScript(title)}""""
        NotifyCommand(listOf(exe, "-e", script))
    },
)

internal fun windowsTools(resolve: (String) -> String?): List<NotifyTool> {
    val findPowershell = {
        val root = System.getenv("SystemRoot") ?: "C:\\Windows"
        val bundled = File(root, "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        when {
            bundled.isFile -> bundled.absolutePath
            else -> resolve("pwsh") ?: resolve("powershell")
        }
    }
    return listOf(
        NotifyTool("powershell-toast", findPowershell) { exe, title, body ->
            NotifyCommand(
                argv = listOf(
                    exe, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-Command", WINDOWS_TOAST_SCRIPT,
                ),
                env = notifyEnv(title, body),
                timeoutMs = 8_000,
            )
        },
        NotifyTool("powershell-balloon", findPowershell) { exe, title, body ->
            NotifyCommand(
                argv = listOf(
                    exe, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-Command", WINDOWS_BALLOON_SCRIPT,
                ),
                env = notifyEnv(title, body),
                timeoutMs = 10_000,
            )
        },
    )
}

private fun notifyEnv(title: String, body: String) = mapOf(
    "AYN_NOTIFY_TITLE" to title,
    "AYN_NOTIFY_BODY" to body,
)

private const val WINDOWS_TOAST_SCRIPT = $$"""
$ErrorActionPreference = 'Stop'
$title = [System.Security.SecurityElement]::Escape($env:AYN_NOTIFY_TITLE)
$body = [System.Security.SecurityElement]::Escape($env:AYN_NOTIFY_BODY)
[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom, ContentType = WindowsRuntime] | Out-Null
$xml = New-Object Windows.Data.Xml.Dom.XmlDocument
$xml.LoadXml(('<toast><visual><binding template="ToastGeneric"><text>' + $title + '</text><text>' + $body + '</text></binding></visual></toast>'))
$toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('AE2 Is All You Need').Show($toast)
"""

private const val WINDOWS_BALLOON_SCRIPT = $$"""
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$n = New-Object System.Windows.Forms.NotifyIcon
$n.Icon = [System.Drawing.SystemIcons]::Information
$n.Visible = $true
$n.ShowBalloonTip(5000, $env:AYN_NOTIFY_TITLE, $env:AYN_NOTIFY_BODY, [System.Windows.Forms.ToolTipIcon]::Info)
Start-Sleep -Milliseconds 5500
$n.Dispose()
"""
