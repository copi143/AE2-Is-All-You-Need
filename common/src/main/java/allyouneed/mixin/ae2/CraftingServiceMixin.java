package allyouneed.mixin.ae2;

import allyouneed.mixin.ACraftingCalculation;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Mixin to replace CraftingService.beginCraftingCalculation with our custom version
 * that uses ACraftingCalculation for adaptive probability patterns.
 */
@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceMixin {
    @Final
    @Shadow
    private static ExecutorService CRAFTING_POOL;
    @Final
    @Shadow
    private IGrid grid;

    /**
     * @author AE2 Is All You Need
     * @reason Replace CraftingCalculation with ACraftingCalculation for adaptive probability patterns
     */
    @Overwrite
    public Future<ICraftingPlan> beginCraftingCalculation(Level level, ICraftingSimulationRequester simRequester, AEKey what, long amount, CalculationStrategy strategy) {
        if (level == null || simRequester == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        final ACraftingCalculation job = new ACraftingCalculation(level, this.grid, simRequester, new GenericStack(what, amount), strategy);

        return CRAFTING_POOL.submit(job::run);
    }
}
