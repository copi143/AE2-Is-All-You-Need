package allyouneed.mixin.ae2;

import appeng.api.networking.events.GridBootingStatusChange;
import appeng.me.Grid;
import appeng.me.service.PathingService;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathingService.class, remap = false)
public abstract class PathingServiceMixin {

    @Final
    @Shadow
    private Grid grid;

    @Unique
    private int allyouneed$bootNotify;

    @Redirect(
            method = "onServerEndTick",
            at = @At(value = "FIELD", target = "Lappeng/me/service/PathingService;booting:Z", opcode = Opcodes.PUTFIELD))
    private void allyouneed$keepBooted(PathingService self, boolean value) {
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void allyouneed$resetBootNotify(CallbackInfo ci) {
        this.allyouneed$bootNotify = 0;
    }

    @Redirect(
            method = "onServerEndTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/me/service/PathingService;postBootingStatusChange()V"))
    private void allyouneed$bootNotify(PathingService self) {
        this.allyouneed$bootNotify++;
        if (this.allyouneed$bootNotify >= 2) {
            this.grid.postEvent(new GridBootingStatusChange(false));
        }
    }
}
