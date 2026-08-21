package allyouneed.util

object DismantleFlags {
    private val wrenching = ThreadLocal.withInitial { false }

    @JvmStatic
    fun setWrenchDismantling(value: Boolean) {
        wrenching.set(value)
    }

    @JvmStatic
    fun isWrenchDismantling(): Boolean = wrenching.get()
}
