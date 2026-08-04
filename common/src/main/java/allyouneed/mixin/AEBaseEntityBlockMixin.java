package allyouneed.mixin;

import allyouneed.mac.MacHosts;
import allyouneed.mac.MacNbt;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = AEBaseEntityBlock.class)
public abstract class AEBaseEntityBlockMixin {

    /**
     * Ordinary breaks call exportSettings(DISMANTLE_ITEM). Always strip MAC so identity is not kept.
     * Wrench path rebuilds the item tag after getDrops and re-exports with MAC via ThreadLocal flag.
     */
    @Inject(method = "getDrops", at = @At("RETURN"))
    private void allyouneed$stripMacOnBreak(BlockState state, LootParams.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> drops = cir.getReturnValue();
        if (drops == null) {
            return;
        }
        for (ItemStack stack : drops) {
            if (stack.hasTag()) {
                MacNbt.stripMacs(stack.getTag());
            }
        }
    }

    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void allyouneed$importMacOnPlace(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        Map<String, Long> macs = MacNbt.readFromStack(stack);
        if (macs.isEmpty()) {
            return;
        }
        AEBaseEntityBlock<?> self = (AEBaseEntityBlock<?>) (Object) this;
        AEBaseBlockEntity be = self.getBlockEntity(level, pos);
        if (be != null) {
            MacHosts.applyMacs(be, macs);
        }
    }
}
