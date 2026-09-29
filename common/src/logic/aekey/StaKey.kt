package allyouneed.logic.aekey

import allyouneed.logic.aekey.helpers.LevelOnlyKey

data class StaKey(override val level: Int = 0) : LevelOnlyKey() {
    override fun getType(): Type = Type

    object Type : LevelOnlyKey.Type<StaKey>("sta", StaKey::class, ::StaKey)
}
