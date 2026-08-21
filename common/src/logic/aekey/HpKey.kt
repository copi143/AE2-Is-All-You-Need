package allyouneed.logic.aekey

import allyouneed.item.packet.AllPackets

data class HpKey(override val level: Int = 0) : LevelOnlyKey() {
    override val packetType: String = AllPackets.TYPE_HP
    override fun getType(): Type = Type

    object Type : LevelOnlyKey.Type<HpKey>("hp", HpKey::class, ::HpKey)
}
