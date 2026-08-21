package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 当物品总数 > 64 时，全部封包为物品封包而非逐个掉落。
 */
@Mixin(value = AEItemKey.class, remap = false)
public abstract class AEItemKeyAddDropsMixin {

    @Inject(method = "addDrops", at = @At("HEAD"), cancellable = true)
    private void allyouneed$packetizeIfExcessive(
        long amount, List<ItemStack> drops, Level level, BlockPos pos,
        CallbackInfo ci
    ) {
        if (amount > 64) {
            AEItemKey self = (AEItemKey) (Object) this;
            drops.add(AllPackets.INSTANCE.createItemPacket(self, amount));
            ci.cancel();
        }
    }
}
