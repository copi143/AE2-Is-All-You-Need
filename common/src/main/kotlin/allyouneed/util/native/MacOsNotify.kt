package allyouneed.util.native

import org.lwjgl.system.JNI
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAddress

internal object MacOsNotify {
    fun send(title: String, body: String): Boolean {
        val objc = loadNativeLibrary(
            "/usr/lib/libobjc.A.dylib",
            "libobjc.A.dylib",
            "objc",
        ) ?: return false
        val objcGetClass = objc.getFunctionAddress("objc_getClass")
        val selRegisterName = objc.getFunctionAddress("sel_registerName")
        val objcMsgSend = objc.getFunctionAddress("objc_msgSend")
        if (objcGetClass == NULL || selRegisterName == NULL || objcMsgSend == NULL) return false

        MemoryStack.stackPush().use { stack ->
            fun cls(name: String) = JNI.invokePP(memAddress(stack.UTF8(name)), objcGetClass)
            fun sel(name: String) = JNI.invokePP(memAddress(stack.UTF8(name)), selRegisterName)
            fun msg(self: Long, selector: Long) = JNI.invokePPP(self, selector, objcMsgSend)
            fun msg(self: Long, selector: Long, arg: Long) = JNI.invokePPPP(self, selector, arg, objcMsgSend)

            val nsString = cls("NSString")
            val nsUserNotification = cls("NSUserNotification")
            val nsUserNotificationCenter = cls("NSUserNotificationCenter")
            if (nsString == NULL || nsUserNotification == NULL || nsUserNotificationCenter == NULL) return false

            val titleNs = msg(nsString, sel("stringWithUTF8String:"), memAddress(stack.UTF8(title)))
            val bodyNs = msg(nsString, sel("stringWithUTF8String:"), memAddress(stack.UTF8(body)))
            val notification = msg(msg(nsUserNotification, sel("alloc")), sel("init"))
            if (notification == NULL) return false
            JNI.invokePPPV(notification, sel("setTitle:"), titleNs, objcMsgSend)
            JNI.invokePPPV(notification, sel("setInformativeText:"), bodyNs, objcMsgSend)
            val center = msg(nsUserNotificationCenter, sel("defaultUserNotificationCenter"))
            if (center == NULL) return false
            JNI.invokePPPV(center, sel("deliverNotification:"), notification, objcMsgSend)
            return true
        }
    }
}
