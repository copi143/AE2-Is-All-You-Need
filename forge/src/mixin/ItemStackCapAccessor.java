package allyouneed.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStack.class)
public interface ItemStackCapAccessor {
    @Accessor(value = "capNBT", remap = false)
    CompoundTag allyouneed$getCapNbt();
}
