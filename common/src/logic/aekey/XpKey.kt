package allyouneed.logic.aekey

import allyouneed.logic.aekey.helpers.LevelOnlyKey

data class XpKey(override val level: Int = 0) : LevelOnlyKey() {
    override fun getType(): Type = Type

    object Type : LevelOnlyKey.Type<XpKey>("xp", XpKey::class, ::XpKey)
}
