package allyouneed.mixin.ae2;

import appeng.parts.AEBasePart;
import appeng.parts.automation.AnnihilationPlanePart;
import appeng.parts.automation.FormationPlanePart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import allyouneed.parts.planebus.PlaneBusClusters;
import allyouneed.parts.planebus.PlaneKind;

/**
 * 把原版破坏面板与成型面板登记进专用线缆的集群注册表，使“整个结构只消耗
 * 1 个频道”对面板生效。目标选在 {@link AEBasePart} 而不是面板类上：
 * {@code removeFromWorld} 仅声明于基类，Mixin 无法注入目标类中不存在的方法；
 * 用 instanceof 守卫限定只处理两类面板。
 * <p>
 * Registers vanilla annihilation and forming planes into the plane-bus cluster registry so
 * that the "one channel per structure" rule also covers planes. The target is
 * {@link AEBasePart} rather than the plane classes because {@code removeFromWorld} is only
 * declared on the base class and mixins cannot inject into methods absent from the target;
 * instanceof guards restrict the hooks to the two plane types.
 */
@Mixin(value = AEBasePart.class, remap = false)
public abstract class AEBasePartPlaneBusRegistryMixin {

    @Inject(method = "addToWorld", at = @At("RETURN"))
    private void allyouneed$registerPlaneMembership(CallbackInfo ci) {
        var part = (AEBasePart) (Object) this;
        if (part instanceof AnnihilationPlanePart) {
            PlaneBusClusters.planeAdded(part.getBlockEntity(), part.getSide(), PlaneKind.ANNIHILATION);
        } else if (part instanceof FormationPlanePart) {
            PlaneBusClusters.planeAdded(part.getBlockEntity(), part.getSide(), PlaneKind.FORMATION);
        }
    }

    @Inject(method = "removeFromWorld", at = @At("HEAD"))
    private void allyouneed$unregisterPlaneMembership(CallbackInfo ci) {
        var part = (AEBasePart) (Object) this;
        if (part instanceof AnnihilationPlanePart || part instanceof FormationPlanePart) {
            PlaneBusClusters.planeRemoved(part.getBlockEntity(), part.getSide());
        }
    }
}
