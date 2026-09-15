package allyouneed.mixin.ae2;

import allyouneed.api.IFluidKeyHolder;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEFluidKey.class, remap = false)
public abstract class AEFluidKeyOfMixin {

    @Inject(method = "of(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEFluidKey;", at = @At("HEAD"), cancellable = true)
    private static void allyouneed$plainFluidHit(Fluid fluid, @Nullable CompoundTag tag, CallbackInfoReturnable<AEFluidKey> cir) {
        if (tag != null) {
            return;
        }
        if (fluid instanceof IFluidKeyHolder holder) {
            AEFluidKey cached = holder.getPlainFluidKey();
            if (cached != null) {
                cir.setReturnValue(cached);
            }
        }
    }

    @Inject(method = "of(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEFluidKey;", at = @At("RETURN"))
    private static void allyouneed$plainFluidStore(Fluid fluid, @Nullable CompoundTag tag, CallbackInfoReturnable<AEFluidKey> cir) {
        if (tag != null) {
            return;
        }
        AEFluidKey key = cir.getReturnValue();
        if (key == null) {
            return;
        }
        if (fluid instanceof IFluidKeyHolder holder && holder.getPlainFluidKey() == null) {
            holder.setPlainFluidKey(key);
        }
    }
}
