package allyouneed.mixin.minecraft;

import allyouneed.api.IItemKeyHolder;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(Item.class)
public abstract class ItemMixin implements IItemKeyHolder {

    @Unique
    @Nullable
    private AEItemKey allyouneed$plainItemKey;

    @Override
    public @Nullable AEItemKey getPlainItemKey() {
        return this.allyouneed$plainItemKey;
    }

    @Override
    public void setPlainItemKey(@Nullable AEItemKey key) {
        this.allyouneed$plainItemKey = key;
    }
}
