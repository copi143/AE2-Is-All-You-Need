package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.me.storage.CompositeStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = CompositeStorage.class, remap = false)
public abstract class CompositeStorageMixin {

    @Shadow
    @Final
    private Map<AEKeyType, MEStorage> storages;

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void allyouneed$convertPacket(
        AEKey what, long amount, Actionable mode, IActionSource source,
        CallbackInfoReturnable<Long> cir
    ) {
        if (!(what instanceof AEItemKey itemKey)) return;

        var stack = itemKey.toStack();
        if (!AllPackets.INSTANCE.isPacket(stack)) return;

        var resourceKey = AllPackets.INSTANCE.toAEKey(stack);
        if (resourceKey == null) {
            cir.setReturnValue(0L);
            return;
        }

        var resourcePerItem = AllPackets.INSTANCE.getResourceAmount(stack);
        if (resourcePerItem <= 0L || amount <= 0L) {
            cir.setReturnValue(0L);
            return;
        }

        var storage = this.storages.get(resourceKey.getType());
        if (storage == null) {
            cir.setReturnValue(0L);
            return;
        }

        if (amount == 1L) {
            var simulated = storage.insert(resourceKey, resourcePerItem, Actionable.SIMULATE, source);
            if (simulated < resourcePerItem) {
                cir.setReturnValue(0L);
                return;
            }
            if (mode == Actionable.MODULATE) {
                storage.insert(resourceKey, resourcePerItem, Actionable.MODULATE, source);
            }
            cir.setReturnValue(1L);
            return;
        }

        long totalResource;
        try {
            totalResource = Math.multiplyExact(resourcePerItem, amount);
        } catch (ArithmeticException e) {
            totalResource = Long.MAX_VALUE;
        }

        var simulated = storage.insert(resourceKey, totalResource, Actionable.SIMULATE, source);
        var complete = simulated / resourcePerItem;
        if (complete <= 0L) {
            cir.setReturnValue(0L);
            return;
        }
        complete = Math.min(complete, amount);
        if (mode == Actionable.MODULATE) {
            storage.insert(resourceKey, complete * resourcePerItem, Actionable.MODULATE, source);
        }
        cir.setReturnValue(complete);
    }
}
