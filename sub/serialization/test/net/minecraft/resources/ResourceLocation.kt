package net.minecraft.resources

data class ResourceLocation(val namespace: String, val path: String) {
    constructor(loc: String) : this(
        loc.substringBefore(":", "minecraft"),
        loc.substringAfter(":", loc)
    )
    override fun toString(): String = "$namespace:$path"
}
