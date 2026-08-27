package appeng.api.stacks

interface KeyContent {
    fun `asm$equals`(other: Any?): Boolean
    fun `asm$hashCode`(): Int
}
