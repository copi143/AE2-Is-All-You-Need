package allyouneed.iodrive

import appeng.menu.AEBaseMenu
import appeng.menu.SlotSemantics
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.slot.RestrictedInputSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType

class MEIODriveMenu(
    id: Int,
    playerInventory: Inventory,
    host: MEIODriveBlockEntity,
) : AEBaseMenu(TYPE, id, playerInventory, host) {

    init {
        val inv = host.getInternalInventory()
        for (i in 0 until MEIODriveBlockEntity.CELL_COUNT) {
            this.addSlot(
                RestrictedInputSlot(
                    RestrictedInputSlot.PlacableItemType.STORAGE_CELLS,
                    inv, i,
                ),
                SlotSemantics.STORAGE_CELL,
            )
        }
        this.createPlayerInventorySlots(playerInventory)
    }

    companion object {
        val TYPE: MenuType<MEIODriveMenu> = MenuTypeBuilder
            .create(::MEIODriveMenu, MEIODriveBlockEntity::class.java)
            .build("me_io_drive")
    }
}
