package allyouneed.mixin.ae2;

import allyouneed.api.BigStackSource;
import allyouneed.item.packet.AllPackets;
import allyouneed.util.bigint.ObjectCounter;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.me.cells.BasicCellInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = BasicCellInventory.class, remap = false)
public abstract class BasicCellInventoryMixin implements BigStackSource {

    @Shadow
    protected abstract Object2LongMap<AEKey> getCellItems();

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void allyouneed$rejectPackets(
        AEKey what, long amount, Actionable mode, IActionSource source,
        CallbackInfoReturnable<Long> cir
    ) {
        if (what instanceof AEItemKey itemKey) {
            if (AllPackets.INSTANCE.isPacket(itemKey.toStack())) {
                cir.setReturnValue(0L);
            }
        }
    }

    @Override
    public void getBigAvailableStacks(ObjectCounter<AEKey> out) {
        for (var entry : Object2LongMaps.fastIterable(this.getCellItems())) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public BigInteger getBigAmount(AEKey what) {
        long amount = this.getCellItems().getLong(what);
        return amount <= 0L ? BigInteger.ZERO : BigInteger.valueOf(amount);
    }

    @Override
    public @Nullable ObjectCounter<AEKey> getLastBigStacks() {
        return null;
    }
}
