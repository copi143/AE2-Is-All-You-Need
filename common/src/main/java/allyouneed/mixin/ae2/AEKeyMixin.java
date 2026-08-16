package allyouneed.mixin.ae2;

import allyouneed.api.KeyIdHolder;
import allyouneed.util.id.KeyIdRegistry;
import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Injects process-global integer identities onto every {@link AEKey}.
 * <p>
 * {@code primaryId}: same item/fluid (NBT ignored).<br>
 * {@code secondaryId}: {@code (primaryId << 32) | local}; {@code local == 0} when
 * {@code dropSecondary()} equals this key.
 * <p>
 * 向每个 AEKey 注入进程全局整数身份。
 * secondaryId 高 32 位为 primaryId；无 secondary 时等于 {@code primaryId << 32}。
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = AEKey.class, remap = false)
public abstract class AEKeyMixin implements KeyIdHolder {

    @Unique
    private int primaryId = -1;

    @Unique
    private long secondaryId = -1L;

    @Unique
    @Override
    public int getPrimaryId() {
        if (this.primaryId < 0) {
            this.primaryId = KeyIdRegistry.assignPrimary((AEKey) (Object) this);
        }
        return this.primaryId;
    }

    @Unique
    @Override
    public long getSecondaryId() {
        if (this.secondaryId < 0) {
            this.secondaryId = KeyIdRegistry.assignSecondary((AEKey) (Object) this);
            if (this.primaryId < 0) {
                this.primaryId = (int) (this.secondaryId >>> 32);
            }
        }
        return this.secondaryId;
    }

    @Unique
    @Override
    public void invalidateKeyIds() {
        this.primaryId = -1;
        this.secondaryId = -1L;
    }
}
