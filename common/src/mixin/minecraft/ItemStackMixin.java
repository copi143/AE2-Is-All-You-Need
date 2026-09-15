package allyouneed.mixin.minecraft;

import allyouneed.api.IItemStackKeyHolder;
import appeng.api.stacks.AEItemKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements IItemStackKeyHolder {

    @Shadow
    @Nullable
    public abstract CompoundTag getTag();

    @Unique
    @Nullable
    private AEItemKey allyouneed$cachedItemKey;

    @Unique
    @Nullable
    private CompoundTag allyouneed$cachedItemKeyTag;

    @Override
    public @Nullable AEItemKey getCachedItemKey() {
        if (this.allyouneed$cachedItemKey != null && this.getTag() == this.allyouneed$cachedItemKeyTag) {
            return this.allyouneed$cachedItemKey;
        }
        return null;
    }

    @Override
    public void setCachedItemKey(@Nullable AEItemKey key) {
        this.allyouneed$cachedItemKey = key;
        this.allyouneed$cachedItemKeyTag = this.getTag();
    }

    @Override
    public void invalidateCachedItemKey() {
        this.allyouneed$cachedItemKey = null;
        this.allyouneed$cachedItemKeyTag = null;
    }

    @Inject(method = "setTag", at = @At("HEAD"))
    private void allyouneed$invalidateOnSetTag(@Nullable CompoundTag tag, CallbackInfo ci) {
        this.invalidateCachedItemKey();
    }

    @Inject(method = "getOrCreateTag", at = @At("HEAD"))
    private void allyouneed$invalidateOnCreateTag(CallbackInfoReturnable<CompoundTag> cir) {
        if (this.getTag() == null) {
            this.invalidateCachedItemKey();
        }
    }
}
