package allyouneed.logic.aekey

import allyouneed.item.packet.AllPackets

data class StaKey(override val level: Int = 0) : LevelOnlyKey() {
    override val packetType: String = AllPackets.TYPE_STA
    override fun getType(): Type = Type

    object Type : LevelOnlyKey.Type<StaKey>("sta", StaKey::class, ::StaKey)
}
