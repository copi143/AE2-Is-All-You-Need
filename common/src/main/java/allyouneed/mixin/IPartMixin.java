package allyouneed.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import allyouneed.mac.MacHosts;
import allyouneed.mac.MacNbt;
import appeng.api.parts.IPart;
import appeng.util.SettingsFrom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Part drops: wrench keeps MAC, normal break does not.
 * Overwrites default {@link IPart#addPartDrop} to branch on {@code wrenched}.
 */
@Mixin(value = IPart.class, remap = false)
public interface IPartMixin {

    /**
     * @author ae2isallyouneed
     * @reason Distinguish wrench vs break for MAC persistence on part item stacks.
     */
    @Overwrite
    default void addPartDrop(List<ItemStack> drops, boolean wrenched) {
        IPart self = (IPart) this;
        ItemStack stack = new ItemStack(self.getPartItem());
        CompoundTag tag = new CompoundTag();
        self.exportSettings(SettingsFrom.DISMANTLE_ITEM, tag);
        if (wrenched) {
            Map<String, Long> macs = MacHosts.collectMacs(self);
            if (!macs.isEmpty()) {
                MacNbt.putMacs(tag, macs);
            }
        } else {
            MacNbt.stripMacs(tag);
        }
        if (!tag.isEmpty()) {
            stack.setTag(tag);
        }
        drops.add(stack);
    }
}
