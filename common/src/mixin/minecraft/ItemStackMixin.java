package allyouneed.mixin.minecraft;

import allyouneed.api.IItemStackKeyHolder;
import allyouneed.util.ItemStackCaps;
import appeng.api.stacks.AEItemKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private boolean allyouneed$canCacheKey() {
        return this.getTag() == null && !ItemStackCaps.hasCaps.invoke((ItemStack) (Object) this);
    }

    @Override
    public @Nullable AEItemKey getCachedItemKey() {
        if (this.allyouneed$cachedItemKey == null) {
            return null;
        }
        if (!this.allyouneed$canCacheKey()) {
            this.invalidateCachedItemKey();
            return null;
        }
        return this.allyouneed$cachedItemKey;
    }

    @Override
    public void setCachedItemKey(@Nullable AEItemKey key) {
        if (key == null || !this.allyouneed$canCacheKey()) {
            this.invalidateCachedItemKey();
            return;
        }
        this.allyouneed$cachedItemKey = key;
    }

    @Override
    public void invalidateCachedItemKey() {
        this.allyouneed$cachedItemKey = null;
    }

    @Inject(method = "setTag", at = @At("HEAD"))
    private void allyouneed$invalidateOnSetTag(@Nullable CompoundTag tag, CallbackInfo ci) {
        this.invalidateCachedItemKey();
    }

    @Inject(method = "getOrCreateTag", at = @At("HEAD"))
    private void allyouneed$invalidateOnCreateTag(CallbackInfoReturnable<CompoundTag> cir) {
        this.invalidateCachedItemKey();
    }

    @Inject(method = "addTagElement", at = @At("HEAD"))
    private void allyouneed$invalidateOnAddTag(String key, Tag tag, CallbackInfo ci) {
        this.invalidateCachedItemKey();
    }

    @Inject(method = "removeTagKey", at = @At("HEAD"))
    private void allyouneed$invalidateOnRemoveTag(String key, CallbackInfo ci) {
        this.invalidateCachedItemKey();
    }

    @Inject(method = "setDamageValue", at = @At("HEAD"))
    private void allyouneed$invalidateOnDamage(int damage, CallbackInfo ci) {
        this.invalidateCachedItemKey();
    }
}
