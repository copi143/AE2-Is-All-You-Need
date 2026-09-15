package allyouneed.mixin.ae2;

import allyouneed.api.IItemKeyHolder;
import allyouneed.api.IItemStackKeyHolder;
import appeng.api.stacks.AEItemKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEItemKey.class, remap = false)
public abstract class AEItemKeyOfMixin {

    @Inject(method = "of(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;", at = @At("HEAD"), cancellable = true)
    private static void allyouneed$plainItemHit(ItemLike itemLike, @Nullable CompoundTag tag, CallbackInfoReturnable<AEItemKey> cir) {
        if (tag != null) {
            return;
        }
        Item item = itemLike.asItem();
        if (item instanceof IItemKeyHolder holder) {
            AEItemKey cached = holder.getPlainItemKey();
            if (cached != null) {
                cir.setReturnValue(cached);
            }
        }
    }

    @Inject(method = "of(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;", at = @At("RETURN"))
    private static void allyouneed$plainItemStore(ItemLike itemLike, @Nullable CompoundTag tag, CallbackInfoReturnable<AEItemKey> cir) {
        if (tag != null) {
            return;
        }
        AEItemKey key = cir.getReturnValue();
        if (key == null) {
            return;
        }
        Item item = itemLike.asItem();
        if (item instanceof IItemKeyHolder holder && holder.getPlainItemKey() == null) {
            holder.setPlainItemKey(key);
        }
    }

    @Inject(method = "of(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;", at = @At("HEAD"), cancellable = true)
    private static void allyouneed$stackHit(ItemStack stack, CallbackInfoReturnable<AEItemKey> cir) {
        if (stack.isEmpty()) {
            return;
        }
        AEItemKey cached = ((IItemStackKeyHolder) (Object) stack).getCachedItemKey();
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "of(Lnet/minecraft/world/item/ItemStack;)Lappeng/api/stacks/AEItemKey;", at = @At("RETURN"))
    private static void allyouneed$stackStore(ItemStack stack, CallbackInfoReturnable<AEItemKey> cir) {
        AEItemKey key = cir.getReturnValue();
        if (key != null) {
            ((IItemStackKeyHolder) (Object) stack).setCachedItemKey(key);
        }
    }
}
