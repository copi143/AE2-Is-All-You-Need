package allyouneed.core

interface ContentIdentity {
    fun `asm$equals`(other: Any?): Boolean

    fun `asm$hashCode`(): Int
}
