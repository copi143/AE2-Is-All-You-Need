package allyouneed.mixin.minecraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(CompoundTag.class)
public interface CompoundTagAccessor {

    @Accessor("tags")
    Map<String, Tag> getTags();
}
