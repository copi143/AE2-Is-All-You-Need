package allyouneed.logic.aekey

import appeng.api.stacks.AEKeyType

enum class AllKeys(val type: AEKeyType) {
    Energy(EnergyKey.Type), //
    Mana(ManaKey.Type), //
    Virtual(VirtualKey.Type), //
    Hp(HpKey.Type), //
    Sta(StaKey.Type), //
    Xp(XpKey.Type), //
    ;
}
