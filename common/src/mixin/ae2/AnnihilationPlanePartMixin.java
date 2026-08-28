package allyouneed.mixin.ae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.parts.AEBasePart;
import appeng.parts.automation.AnnihilationPlanePart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 上游修复（AppliedEnergistics2#8080，1.21 起）的 1.20.1 回溯：破坏面板的
 * {@code tickingRequest} 原本不检查节点激活状态，导致面板在缺频道甚至离线时仍然
 * 破坏方块。补上守卫后，“频道”对面板重新产生约束——破坏面板专用线缆的
 * “整个结构消耗一个频道”也因此有了意义。
 * <p>
 * Backport of upstream fix AppliedEnergistics2#8080 (first shipped for MC 1.21):
 * the annihilation plane's {@code tickingRequest} never checked node activity, so planes
 * kept breaking blocks without a channel or even offline. The guard makes channels matter
 * again, which is what gives the annihilation plane bus's "one channel per structure"
 * semantics teeth.
 */
@Mixin(value = AnnihilationPlanePart.class, remap = false)
public abstract class AnnihilationPlanePartMixin {

    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true)
    private void allyouneed$sleepWhenInactive(IGridNode node, int ticksSinceLastCall, CallbackInfoReturnable<TickRateModulation> cir) {
        if (!((AEBasePart) (Object) this).isActive()) {
            cir.setReturnValue(TickRateModulation.SLEEP);
        }
    }
}
