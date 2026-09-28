package allyouneed.logic.aekey

import allyouneed.item.packet.AllPackets
import allyouneed.logic.aekey.helpers.LevelOnlyKey

data class XpKey(override val level: Int = 0) : LevelOnlyKey() {
    override val packetType: String = AllPackets.TYPE_XP
    override fun getType(): Type = Type

    object Type : LevelOnlyKey.Type<XpKey>("xp", XpKey::class, ::XpKey)
}
