package kaptor.a2s.runtime

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Hidden Class 加载器：用 `defineHiddenClass` 定义隐藏类，并暴露 MethodHandle。
 *
 * 隐藏类不绑定 ClassLoader、不写磁盘、释放引用后可被 GC，实现「部分释放」。
 */
class A2sHiddenClassLoader(private val lookup: MethodHandles.Lookup) {

    data class DefinedClass(
        val clazz: Class<*>,
        val lookup: MethodHandles.Lookup,
    )

    fun define(bytes: ByteArray): DefinedClass {
        val hcLookup = lookup.defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE)
        return DefinedClass(hcLookup.lookupClass(), hcLookup)
    }

    fun findConstructor(clazz: Class<*>, hcLookup: MethodHandles.Lookup, paramTypes: List<Class<*>>): MethodHandle {
        return hcLookup.findConstructor(clazz, MethodType.methodType(Void.TYPE, paramTypes))
    }

    fun findVirtual(hcLookup: MethodHandles.Lookup, clazz: Class<*>, name: String, returnType: Class<*>, paramTypes: List<Class<*>>): MethodHandle {
        return hcLookup.findVirtual(clazz, name, MethodType.methodType(returnType, paramTypes))
    }

    companion object {
        /** 从指定宿主类获取带 PRIVATE 权限的 lookup。 */
        fun lookupFor(host: Class<*>): MethodHandles.Lookup = MethodHandles.privateLookupIn(host, MethodHandles.lookup())
    }
}
