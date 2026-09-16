package allyouneed.mixin.forge;

import allyouneed.util.inventory.HandlerChangeNotifiers;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InvWrapper.class, remap = false)
public abstract class InvWrapperMixin {

    @Inject(method = "insertItem", at = @At("RETURN"))
    private void allyouneed$insert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        HandlerChangeNotifiers.onInsert(this, stack, simulate, cir.getReturnValue());
    }

    @Inject(method = "extractItem", at = @At("RETURN"))
    private void allyouneed$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        HandlerChangeNotifiers.onExtract(this, simulate, cir.getReturnValue());
    }

    @Inject(method = "setStackInSlot", at = @At("TAIL"))
    private void allyouneed$set(int slot, ItemStack stack, CallbackInfo ci) {
        HandlerChangeNotifiers.onSlotSet(this);
    }
}
