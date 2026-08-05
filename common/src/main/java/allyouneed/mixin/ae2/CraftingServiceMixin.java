package allyouneed.mixin.ae2;

import allyouneed.logic.AE2TaskScheduler;
import allyouneed.logic.crafting.ACraftingCalculation;
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

import java.util.concurrent.Future;

/**
 * Mixin to replace CraftingService.beginCraftingCalculation with our custom version
 * that uses ACraftingCalculation and AE2TaskScheduler for adaptive probability patterns.
 *
 * Inventory/pattern snapshots are taken in the ACraftingCalculation constructor on this
 * (calling) thread; only pure computation is submitted to the shared background pool.
 */
@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceMixin {
    @Final
    @Shadow
    private IGrid grid;

    /**
     * @author AE2 Is All You Need
     * @reason Replace CraftingCalculation with ACraftingCalculation + AE2TaskScheduler
     */
    @Overwrite
    public Future<ICraftingPlan> beginCraftingCalculation(Level level, ICraftingSimulationRequester simRequester, AEKey what, long amount, CalculationStrategy strategy) {
        if (level == null || simRequester == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        // MUST snapshot on this (calling) thread before submit — see docs/Crafting-Calculation.md
        final ACraftingCalculation job = new ACraftingCalculation(
                level, this.grid, simRequester, new GenericStack(what, amount), strategy);

        return AE2TaskScheduler.submit(job::run);
    }
}
