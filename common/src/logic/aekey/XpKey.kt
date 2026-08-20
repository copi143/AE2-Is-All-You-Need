package allyouneed.logic.aekey

data class XpKey(override val level: Int = 0) : LevelOnlyKey() {
    override fun getType(): Type = Type

    object Type : LevelOnlyKey.Type<XpKey>("xp", XpKey::class, ::XpKey)
}
