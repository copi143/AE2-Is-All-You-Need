package allyouneed.logic.aekey

import allyouneed.logic.aekey.helpers.LevelOnlyKey

data class HpKey(override val level: Int = 0) : LevelOnlyKey() {
    override fun getType(): Type = Type

    object Type : LevelOnlyKey.Type<HpKey>("hp", HpKey::class, ::HpKey)
}
