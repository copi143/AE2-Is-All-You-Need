package allyouneed.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.inv.CraftingSimulationState;
import allyouneed.pattern.adaptive.AdaptiveStatisticalPattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Custom CraftingTreeProcess that intercepts adaptive statistical patterns
 * and scales their inputs by ceil(N/p) instead of using binomial distribution.
 */
public class ACraftingTreeProcess {

    private final ACraftingTreeNode parent;
    final IPatternDetails details;
    private final ACraftingCalculation job;
    private final Map<ACraftingTreeNode, Long> nodes = new LinkedHashMap<>();
    boolean possible = true;
    private boolean containerItems;
    private boolean limitQty;

    public ACraftingTreeProcess(ICraftingService cc, ACraftingCalculation job,
                                IPatternDetails details,
                                ACraftingTreeNode craftingTreeNode) {
        this.parent = craftingTreeNode;
        this.details = details;
        this.job = job;

        updateLimitQty();

        final IPatternDetails.IInput[] inputs = this.details.getInputs();
        for (int x = 0; x < inputs.length; ++x) {
            var input = inputs[x];
            var firstInput = input.getPossibleInputs()[0];
            this.nodes.put(new ACraftingTreeNode(cc, job, firstInput.what(), firstInput.amount(), this, x),
                    input.getMultiplier());
        }
    }

    boolean notRecursive(IPatternDetails details) {
        return this.parent == null || this.parent.notRecursive(details);
    }

    private void updateLimitQty() {
        for (IPatternDetails.IInput input : details.getInputs()) {
            var primaryInput = input.getPossibleInputs()[0];
            boolean isAnInput = false;

            for (var output : details.getOutputs()) {
                if (output.what().matches(primaryInput)) {
                    isAnInput = true;
                    break;
                }
            }

            if (isAnInput) {
                this.limitQty = true;
            }

            if (input.getRemainingKey(primaryInput.what()) != null) {
                this.limitQty = this.containerItems = true;
            }
        }
    }

    boolean limitsQuantity() {
        return this.limitQty;
    }

    void request(CraftingSimulationState inv, long times)
            throws CraftBranchFailure, InterruptedException {
        this.job.handlePausing();

        var containerItems = this.containerItems ? new KeyCounter() : null;

        for (var entry : this.nodes.entrySet()) {
            entry.getKey().request(inv, entry.getValue() * times, containerItems);
        }

        if (containerItems != null) {
            for (var stack : containerItems) {
                inv.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
                inv.addStackBytes(stack.getKey(), stack.getLongValue(), 1);
            }
        }

        for (var out : this.details.getOutputs()) {
            inv.insert(out.what(), out.amount() * times, Actionable.MODULATE);
        }

        inv.addCrafting(details, times);
        inv.addBytes(times);
    }

    long getNodeCount() {
        long tot = 0;
        for (ACraftingTreeNode node : this.nodes.keySet()) {
            tot += node.getNodeCount();
        }
        return tot;
    }

    long getOutputCount(AEKey what) {
        long tot = 0;
        for (var is : this.details.getOutputs()) {
            if (what.matches(is)) {
                tot += is.amount();
            }
        }
        return tot;
    }

    boolean hasMultiplePaths() {
        for (var entry : nodes.entrySet()) {
            if (entry.getKey().hasMultiplePaths()) {
                return true;
            }
        }
        return false;
    }

    double getSuccessProbability() {
        double ownProb = 1.0;
        if (this.details instanceof AdaptiveStatisticalPattern asp) {
            ownProb = 1.0 - (1.0 - asp.getProbability());
        }
        double childProb = 1.0;
        for (var entry : this.nodes.entrySet()) {
            childProb *= entry.getKey().getSuccessProbability();
        }
        return ownProb * childProb;
    }
}
