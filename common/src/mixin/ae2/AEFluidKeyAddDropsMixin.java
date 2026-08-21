package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 流体不再直接消失，改为掉落流体封包。
 */
@Mixin(value = AEFluidKey.class, remap = false)
public abstract class AEFluidKeyAddDropsMixin {

    @Inject(method = "addDrops", at = @At("HEAD"), cancellable = true)
    private void allyouneed$dropFluidPacket(
        long amount, List<ItemStack> drops, Level level, BlockPos pos,
        CallbackInfo ci
    ) {
        if (amount > 0) {
            AEFluidKey self = (AEFluidKey) (Object) this;
            drops.add(AllPackets.INSTANCE.createFluidPacket(self, amount));
            ci.cancel();
        }
    }
}
