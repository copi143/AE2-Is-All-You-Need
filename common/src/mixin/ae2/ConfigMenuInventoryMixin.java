package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 ConfigMenuInventory.convertToSuitableStack() 中拦截封包物品，
 * 在 AEItemKey.of(stack) 之前将其转换为真实的资源 AEKey。
 * 这是幽灵槽（接口配置槽等）的入口，确保玩家放置封包时显示为对应资源类型。
 */
@Mixin(value = ConfigMenuInventory.class, remap = false)
public abstract class ConfigMenuInventoryMixin implements InternalInventory {

    @Shadow
    @Final
    private GenericStackInv inv;

    @Inject(method = "convertToSuitableStack", at = @At("HEAD"), cancellable = true)
    private void allyouneed$convertPacket(ItemStack stack, CallbackInfoReturnable<GenericStack> cir) {
        if (stack.isEmpty()) return;
        if (!AllPackets.INSTANCE.isPacket(stack)) return;

        var resourceKey = AllPackets.INSTANCE.toAEKey(stack);
        if (resourceKey == null) {
            cir.setReturnValue(null);
            return;
        }

        var resourcePerItem = AllPackets.INSTANCE.getResourceAmount(stack);
        var count = stack.getCount();

        if (inv.isAllowed(resourceKey)) {
            cir.setReturnValue(new GenericStack(resourceKey, count * resourcePerItem));
        } else {
            cir.setReturnValue(null);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        var leftover = AllPackets.INSTANCE.insert(stack, stack.getCount(), simulate,
            (key, amount, mode) -> this.inv.insert(slot, key, amount, mode));
        if (leftover != null) {
            return leftover;
        }
        return InternalInventory.super.insertItem(slot, stack, simulate);
    }
}
