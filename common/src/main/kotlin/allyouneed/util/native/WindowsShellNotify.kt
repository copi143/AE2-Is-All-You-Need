package allyouneed.util.native

import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.windows.User32
import org.lwjgl.system.windows.WinBase

internal object WindowsShellNotify {
    private const val HWND_MESSAGE = -3L
    private const val NIM_ADD = 0
    private const val NIM_DELETE = 2
    private const val NIF_ICON = 0x00000002
    private const val NIF_TIP = 0x00000004
    private const val NIF_INFO = 0x00000010
    private const val NIIF_INFO = 0x00000001
    private const val NID_SIZE = 952
    private const val OFF_CB_SIZE = 0
    private const val OFF_HWND = 8
    private const val OFF_UID = 16
    private const val OFF_FLAGS = 20
    private const val OFF_ICON = 32
    private const val OFF_TIP = 40
    private const val OFF_INFO = 304
    private const val OFF_TIMEOUT = 816
    private const val OFF_TITLE = 820
    private const val OFF_INFO_FLAGS = 948

    @Volatile
    private var shellNotifyIcon = NULL

    fun send(title: String, body: String): Boolean {
        val fn = shellNotify()
        if (fn == NULL) return false
        val hwnd = User32.CreateWindowEx(
            0, "STATIC", "ayn-notify",
            0, 0, 0, 0, 0,
            HWND_MESSAGE, NULL, NULL, NULL,
        )
        if (hwnd == NULL) return false
        val icon = User32.nLoadIcon(NULL, User32.IDI_INFORMATION.toLong())
        val data = MemoryUtil.memCalloc(NID_SIZE)
        try {
            val addr = MemoryUtil.memAddress(data)
            MemoryUtil.memPutInt(addr + OFF_CB_SIZE, NID_SIZE)
            MemoryUtil.memPutAddress(addr + OFF_HWND, hwnd)
            MemoryUtil.memPutInt(addr + OFF_UID, 1)
            MemoryUtil.memPutInt(addr + OFF_FLAGS, NIF_ICON or NIF_TIP or NIF_INFO)
            MemoryUtil.memPutAddress(addr + OFF_ICON, icon)
            putWString(addr + OFF_TIP, 128, title)
            putWString(addr + OFF_INFO, 256, body)
            MemoryUtil.memPutInt(addr + OFF_TIMEOUT, 5000)
            putWString(addr + OFF_TITLE, 64, title)
            MemoryUtil.memPutInt(addr + OFF_INFO_FLAGS, NIIF_INFO)
            val added = JNI.invokePI(NIM_ADD, addr, fn) != 0
            if (added) {
                Thread.sleep(200)
                JNI.invokePI(NIM_DELETE, addr, fn)
            }
            return added
        } finally {
            MemoryUtil.memFree(data)
            User32.DestroyWindow(hwnd)
        }
    }

    private fun shellNotify(): Long {
        if (shellNotifyIcon != NULL) return shellNotifyIcon
        synchronized(this) {
            if (shellNotifyIcon != NULL) return shellNotifyIcon
            val shell32 = WinBase.LoadLibrary("shell32")
            if (shell32 == NULL) return NULL
            shellNotifyIcon = WinBase.GetProcAddress(shell32, "Shell_NotifyIconW")
            return shellNotifyIcon
        }
    }

    private fun putWString(addr: Long, maxChars: Int, text: String) {
        val limit = (maxChars - 1).coerceAtLeast(0)
        var i = 0
        for (c in text) {
            if (i >= limit) break
            MemoryUtil.memPutShort(addr + i * 2L, c.code.toShort())
            i++
        }
        MemoryUtil.memPutShort(addr + i * 2L, 0)
    }
}
