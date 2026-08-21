package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.me.cells.BasicCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止封包物品进入 AE 物品存储。
 */
@Mixin(value = BasicCellInventory.class, remap = false)
public abstract class BasicCellInventoryMixin {

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void allyouneed$rejectPackets(
        AEKey what, long amount, Actionable mode, IActionSource source,
        CallbackInfoReturnable<Long> cir
    ) {
        if (what instanceof AEItemKey itemKey) {
            if (AllPackets.INSTANCE.isPacket(itemKey.toStack())) {
                cir.setReturnValue(0L);
            }
        }
    }
}
