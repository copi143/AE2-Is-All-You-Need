package allyouneed.resgen

/** A complete, named set of cube textures. */
class CubeFaces internal constructor(private val textures: Map<String, String>) {
    internal operator fun get(face: String): String = textures.getValue(face)
}

@AssetGenDsl
class CubeFacesBuilder {
    private var defaultTexture: String? = null
    private val textures = linkedMapOf<String, String>()

    /** Default for faces without an explicit texture, independent of declaration order. */
    fun all(texture: String) {
        require(texture.isNotBlank()) { "Face texture must not be blank" }
        defaultTexture = texture
    }

    fun north(texture: String) = face("north", texture)
    fun east(texture: String) = face("east", texture)
    fun south(texture: String) = face("south", texture)
    fun west(texture: String) = face("west", texture)
    fun up(texture: String) = face("up", texture)
    fun down(texture: String) = face("down", texture)

    private fun face(name: String, texture: String) {
        require(texture.isNotBlank()) { "Texture for $name must not be blank" }
        textures[name] = texture
    }

    internal fun build(): CubeFaces {
        val names = listOf("north", "east", "south", "west", "up", "down")
        val missing = names.filter { it !in textures && defaultTexture == null }
        require(missing.isEmpty()) { "Missing cube face textures: ${missing.joinToString()}" }
        return CubeFaces(names.associateWith { textures[it] ?: checkNotNull(defaultTexture) })
    }
}

fun faces(init: CubeFacesBuilder.() -> Unit): CubeFaces = CubeFacesBuilder().apply(init).build()
