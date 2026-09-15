package appeng.api.stacks

abstract class AEKeyAsm : AEKey(), KeyContent {
    @Transient
    private var cachedSecondaryDropped: AEKey? = null

    final override fun equals(other: Any?): Boolean = this === other

    final override fun hashCode(): Int = System.identityHashCode(this)

    final override fun dropSecondary(): AEKey {
        cachedSecondaryDropped?.let { return it }
        val dropped = `asm$dropSecondary`()
        cachedSecondaryDropped = dropped
        return dropped
    }

    abstract fun `asm$dropSecondary`(): AEKey
}
