package allyouneed.mixin.forge;

import allyouneed.util.inventory.InventoryWatchers;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidTank.class, remap = false)
public abstract class FluidTankMixin {

    @Inject(method = "onContentsChanged", at = @At("TAIL"))
    private void allyouneed$notify(CallbackInfo ci) {
        InventoryWatchers.notifyHandler(this);
    }

    @Inject(method = "fill", at = @At("RETURN"))
    private void allyouneed$fill(FluidStack resource, IFluidHandler.FluidAction action, CallbackInfoReturnable<Integer> cir) {
        if (action.execute() && cir.getReturnValue() > 0) {
            InventoryWatchers.notifyHandler(this);
        }
    }

    @Inject(method = "drain(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;", at = @At("RETURN"))
    private void allyouneed$drainStack(FluidStack resource, IFluidHandler.FluidAction action, CallbackInfoReturnable<FluidStack> cir) {
        if (action.execute() && cir.getReturnValue() != null && !cir.getReturnValue().isEmpty()) {
            InventoryWatchers.notifyHandler(this);
        }
    }

    @Inject(method = "drain(ILnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;", at = @At("RETURN"))
    private void allyouneed$drainAmount(int maxDrain, IFluidHandler.FluidAction action, CallbackInfoReturnable<FluidStack> cir) {
        if (action.execute() && cir.getReturnValue() != null && !cir.getReturnValue().isEmpty()) {
            InventoryWatchers.notifyHandler(this);
        }
    }
}
