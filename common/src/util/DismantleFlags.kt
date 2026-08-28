package allyouneed.util

object DismantleFlags {
    private val wrenching = ThreadLocal.withInitial { false }

    @JvmStatic
    fun setWrenchDismantling(value: Boolean) {
        if (value) wrenching.set(true) else wrenching.remove()
    }

    @JvmStatic
    fun isWrenchDismantling(): Boolean = wrenching.get()

    @JvmStatic
    fun clear() = wrenching.remove()
}
