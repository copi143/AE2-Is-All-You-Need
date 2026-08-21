package allyouneed.mixin.ae2;

import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {
    @Accessor("finalOutput")
    GenericStack allyouneed$getFinalOutput();
}
