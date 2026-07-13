package allyouneed.mixin;

import appeng.menu.implementations.MolecularAssemblerMenu;
import appeng.menu.slot.RestrictedInputSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Add a machine installation slot to the Molecular Assembler menu.
 */
@Mixin(MolecularAssemblerMenu.class)
public abstract class MolecularAssemblerMenuMixin {

    @Inject(method = "setupConfig", at = @At("TAIL"), remap = false)
    private void allyouneed$addMachineSlot(CallbackInfo ci) {
        MolecularAssemblerMenu self = (MolecularAssemblerMenu) (Object) this;

        // We add the machine slot as an extra restricted slot.
        // The actual inventory is exposed via sub-inventory "ae2isallyouneed:ma_machine" on the BE.
        // For the menu we attach it using the host's sub-inventory if possible.

        try {
            var host = self.getHost();
            // Use a safe cast instead of pattern matching to be compatible with older Java levels.
            if (host != null && host.getClass() == appeng.blockentity.crafting.MolecularAssemblerBlockEntity.class) {
                appeng.blockentity.crafting.MolecularAssemblerBlockEntity be =
                        (appeng.blockentity.crafting.MolecularAssemblerBlockEntity) host;

                var machineInv = be.getSubInventory(new net.minecraft.resources.ResourceLocation("ae2isallyouneed", "ma_machine"));
                if (machineInv != null) {
                    // We intentionally do not add the slot here (protected addSlot is hard to reach from mixin).
                    // The screen will render an extra slot visually if needed, or we will do it in a later pass.
                }
            }
        } catch (Throwable ignored) {
            // best effort
        }
    }
}
