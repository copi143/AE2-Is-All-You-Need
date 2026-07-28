package allyouneed.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import allyouneed.pattern.adaptive.AdaptiveStatisticalPattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom CraftingTreeNode that intercepts adaptive statistical patterns
 * and scales their inputs by ceil(N/p) instead of using binomial distribution.
 */
public class ACraftingTreeNode {

    @Nullable
    final IPatternDetails.IInput parentInput;
    private final ACraftingCalculation job;
    private final ACraftingTreeProcess parent;
    private final Level level;
    private final AEKey what;
    private final long amount;
    private ArrayList<ACraftingTreeProcess> nodes = null;
    private final boolean canEmit;
    private long adaptiveTotalRequested;

    public ACraftingTreeNode(ICraftingService cc, ACraftingCalculation job, AEKey what, long amount,
                             ACraftingTreeProcess par, int slot) {
        this.parent = par;
        this.parentInput = slot == -1 ? null : par.details.getInputs()[slot];
        this.level = job.getLevel();
        this.job = job;
        this.what = findCraftedStack(cc, what);
        this.amount = amount;
        this.canEmit = cc.canEmitFor(what);
    }

    private AEKey findCraftedStack(ICraftingService cc, AEKey wat) {
        if (cc.canEmitFor(wat)) {
            return wat;
        }

        var patterns = cc.getCraftingFor(wat);

        if (patterns.isEmpty() && parentInput != null) {
            long acceptableAmount = parentInput.getPossibleInputs()[0].amount();

            for (var possibleInput : parentInput.getPossibleInputs()) {
                if (possibleInput.amount() != acceptableAmount) {
                    continue;
                }

                var fuzzy = cc.getFuzzyCraftable(possibleInput.what(), fuzzyCandidate ->
                        this.parentInput.isValid(fuzzyCandidate, level));

                if (fuzzy != null) {
                    return fuzzy;
                }
            }
        }

        return wat;
    }

    private void buildChildPatterns() {
        if (this.canEmit) {
            throw new IllegalStateException("Internal AE2 error: this node is emitable, it shouldn't use patterns!");
        }

        if (this.nodes == null) {
            this.nodes = new ArrayList<>();

            var gridNode = this.job.simRequester.getGridNode();

            if (gridNode != null) {
                var craftingService = gridNode.getGrid().getCraftingService();

                for (var details : wrapPatternsForNode(craftingService, this.what)) {
                    if (this.parent == null || this.parent.notRecursive(details)) {
                        this.nodes.add(new ACraftingTreeProcess(craftingService, job, details, this));
                    }
                }
            }
        }
    }

    private Collection<IPatternDetails> wrapPatternsForNode(ICraftingService service, AEKey whatToCraft) {
        var patterns = service.getCraftingFor(whatToCraft);
        if (this.adaptiveTotalRequested <= 0) {
            return patterns;
        }
        var result = new ArrayList<IPatternDetails>(patterns.size());
        for (var p : patterns) {
            if (p instanceof AdaptiveStatisticalPattern asp) {
                result.add(asp.forRequest(this.adaptiveTotalRequested));
            } else {
                result.add(p);
            }
        }
        return result;
    }

    boolean notRecursive(IPatternDetails details) {
        for (var output : details.getOutputs()) {
            if (this.what.matches(output)) {
                return false;
            }
        }

        for (var input : details.getInputs()) {
            if (this.what.matches(input.getPossibleInputs()[0])) {
                return false;
            }
        }

        if (this.parent == null) {
            return true;
        }

        return this.parent.notRecursive(details);
    }

    void request(CraftingSimulationState inv, long requestedAmount, @Nullable KeyCounter containerItems) throws CraftBranchFailure, InterruptedException {
        this.adaptiveTotalRequested = requestedAmount * this.amount;

        this.job.handlePausing();

        inv.addStackBytes(what, amount, requestedAmount);

        // 1) COLLECT ITEMS FROM THE INVENTORY
        for (var template : getValidItemTemplates(inv)) {
            long extracted = CraftingCpuHelper.extractTemplates(inv, template, requestedAmount);

            if (extracted > 0) {
                requestedAmount -= extracted;
                addContainerItems(template.key(), extracted, containerItems);

                if (requestedAmount == 0) {
                    return;
                }
            }
        }

        addContainerItems(what, requestedAmount, containerItems);

        // 2) EMITABLE ITEMS
        if (this.canEmit) {
            inv.emitItems(this.what, this.amount * requestedAmount);
            return;
        }

        // 3) USE PATTERNS
        buildChildPatterns();
        long totalRequestedItems = requestedAmount * this.amount;
        if (this.nodes.size() == 1) {
            final ACraftingTreeProcess pro = this.nodes.get(0);
            var craftedPerPattern = pro.getOutputCount(this.what);

            while (pro.possible && totalRequestedItems > 0) {
                long times;
                if (pro.limitsQuantity()) {
                    times = 1;
                } else {
                    times = (totalRequestedItems + craftedPerPattern - 1) / craftedPerPattern;
                }
                pro.request(inv, times);

                var available = inv.extract(this.what, totalRequestedItems, Actionable.MODULATE);
                if (available != 0) {
                    totalRequestedItems -= available;

                    if (totalRequestedItems <= 0) {
                        return;
                    }
                } else {
                    var pattern = pro.details.getDefinition();
                    String outputs = Arrays.stream(pro.details.getOutputs())
                            .map(GenericStack::toString)
                            .collect(Collectors.joining(", "));
                    String errorMessage = """
                            Unexpected error in the crafting calculation: can't find created items.
                            This is an AE2 bug, please report it, with the following important information:

                            - Found none of %s. Remaining request: %d of %d*%d.
                            - Tried crafting %d times the pattern %s.
                            - Pattern outputs: %s.
                            """.formatted(what, totalRequestedItems, requestedAmount, amount, times, pattern, outputs);
                    throw new UnsupportedOperationException(errorMessage);
                }
            }
        } else if (this.nodes.size() > 1) {
            for (ACraftingTreeProcess pro : this.nodes) {
                try {
                    while (pro.possible && totalRequestedItems > 0) {
                        final ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
                        pro.request(child, 1);

                        var available = child.extract(this.what, totalRequestedItems, Actionable.MODULATE);

                        if (available != 0) {
                            child.applyDiff(inv);

                            totalRequestedItems -= available;

                            if (totalRequestedItems <= 0) {
                                return;
                            }
                        } else {
                            pro.possible = false;
                        }
                    }
                } catch (CraftBranchFailure fail) {
                    pro.possible = true;
                }
            }
        }

        if (this.job.isSimulation()) {
            job.addMissing(this.what, totalRequestedItems);
        } else {
            throw new CraftBranchFailure(this.what, totalRequestedItems);
        }
    }

    private void addContainerItems(AEKey template, long multiplier, @Nullable KeyCounter outputList) {
        if (outputList != null) {
            var containerItem = parentInput.getRemainingKey(template);
            if (containerItem != null) {
                outputList.add(containerItem, multiplier);
            }
        }
    }

    private Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv) {
        if (this.parentInput == null)
            return List.of(new InputTemplate(what, 1));
        return CraftingCpuHelper.getValidItemTemplates(inv, this.parentInput, level);
    }

    long getNodeCount() {
        long tot = 1;
        if (this.nodes != null) {
            for (ACraftingTreeProcess pro : this.nodes) {
                tot += pro.getNodeCount();
            }
        }
        return tot;
    }

    boolean hasMultiplePaths() {
        if (this.nodes == null) {
            return false;
        }
        if (this.nodes.size() > 1) {
            return true;
        }
        for (var pro : this.nodes) {
            if (pro.hasMultiplePaths()) {
                return true;
            }
        }
        return false;
    }

    double getSuccessProbability() {
        if (this.nodes == null || this.nodes.isEmpty()) {
            return 1.0;
        }
        if (this.nodes.size() == 1) {
            return this.nodes.get(0).getSuccessProbability();
        }
        double failProb = 1.0;
        for (var pro : this.nodes) {
            failProb *= (1.0 - pro.getSuccessProbability());
        }
        return 1.0 - failProb;
    }
}
