package allyouneed.mixin.ae2;

import allyouneed.item.packet.AllPackets;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GenericStackInv.class, remap = false)
public abstract class GenericStackInvMixin {

    @Unique
    private static final ThreadLocal<Boolean> allyouneed$converting = ThreadLocal.withInitial(() -> false);

    @ModifyVariable(method = "setStack", at = @At("HEAD"), argsOnly = true)
    private GenericStack allyouneed$convertPacketStack(GenericStack stack) {
        if (stack == null) return stack;
        if (!(stack.what() instanceof AEItemKey itemKey)) return stack;
        var itemStack = itemKey.toStack();
        if (!AllPackets.INSTANCE.isPacket(itemStack)) return stack;
        var resourceKey = AllPackets.INSTANCE.toAEKey(itemStack);
        if (resourceKey == null) return null;
        var resourcePerItem = AllPackets.INSTANCE.getResourceAmount(itemStack);
        if (resourcePerItem <= 0L) return null;
        var complete = stack.amount();
        if (complete > 1L) {
            var maxResource = ((GenericStackInv) (Object) this).getMaxAmount(resourceKey);
            var maxComplete = maxResource / resourcePerItem;
            if (maxComplete <= 0L) return null;
            complete = Math.min(complete, maxComplete);
        }
        return new GenericStack(resourceKey, complete * resourcePerItem);
    }

    @Inject(method = "getMaxAmount", at = @At("HEAD"), cancellable = true)
    private void allyouneed$packetMaxAmount(AEKey key, CallbackInfoReturnable<Long> cir) {
        if (!(key instanceof AEItemKey itemKey)) return;
        var itemStack = itemKey.toStack();
        if (!AllPackets.INSTANCE.isPacket(itemStack)) return;
        var resourceKey = AllPackets.INSTANCE.toAEKey(itemStack);
        if (resourceKey == null) {
            cir.setReturnValue(0L);
            return;
        }
        var resourcePerItem = AllPackets.INSTANCE.getResourceAmount(itemStack);
        if (resourcePerItem <= 0L) {
            cir.setReturnValue(0L);
            return;
        }
        var maxResource = ((GenericStackInv) (Object) this).getMaxAmount(resourceKey);
        cir.setReturnValue(maxResource / resourcePerItem);
    }

    @Inject(method = "insert(ILappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J", at = @At("HEAD"), cancellable = true)
    private void allyouneed$convertPacketInsert(int slot, AEKey what, long amount, Actionable mode, CallbackInfoReturnable<Long> cir) {
        if (allyouneed$converting.get()) return;
        if (!(what instanceof AEItemKey itemKey)) return;
        var itemStack = itemKey.toStack();
        if (!AllPackets.INSTANCE.isPacket(itemStack)) return;

        var resourceKey = AllPackets.INSTANCE.toAEKey(itemStack);
        if (resourceKey == null) {
            cir.setReturnValue(0L);
            return;
        }
        var resourcePerItem = AllPackets.INSTANCE.getResourceAmount(itemStack);
        if (resourcePerItem <= 0L || amount <= 0L) {
            cir.setReturnValue(0L);
            return;
        }

        allyouneed$converting.set(true);
        try {
            var inv = (GenericStackInv) (Object) this;
            if (amount == 1L) {
                var simulated = inv.insert(slot, resourceKey, resourcePerItem, Actionable.SIMULATE);
                if (simulated < resourcePerItem) {
                    cir.setReturnValue(0L);
                    return;
                }
                if (mode == Actionable.MODULATE) {
                    inv.insert(slot, resourceKey, resourcePerItem, Actionable.MODULATE);
                }
                cir.setReturnValue(1L);
                return;
            }

            long total;
            try {
                total = Math.multiplyExact(resourcePerItem, amount);
            } catch (ArithmeticException e) {
                total = Long.MAX_VALUE;
            }
            var simulated = inv.insert(slot, resourceKey, total, Actionable.SIMULATE);
            var complete = simulated / resourcePerItem;
            if (complete <= 0L) {
                cir.setReturnValue(0L);
                return;
            }
            complete = Math.min(complete, amount);
            if (mode == Actionable.MODULATE) {
                inv.insert(slot, resourceKey, complete * resourcePerItem, Actionable.MODULATE);
            }
            cir.setReturnValue(complete);
        } finally {
            allyouneed$converting.set(false);
        }
    }
}
