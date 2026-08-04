package allyouneed.mixin.gtceu;

import allyouneed.gt.IGroupedBlockPattern;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Group-aware JEI / in-world preview page enumeration.
 *
 * <p>The stock {@link MultiblockMachineDefinition#getMatchingShapes()} DFS enumerates every
 * {@code aisleRepetitions} dimension as {@code [min..max]}, so a group aisle with
 * {@code [0, 16]} would put the 0-bay page first. The in-world preview
 * ({@code MultiblockInWorldPreviewRenderer.showPreview}) renders
 * {@code getMatchingShapes().get(0)}, so the first page must show one bay. This overwrite orders a
 * grouped dimension as {@code 1..max} followed by {@code 0}.
 */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class MultiblockMachineDefinitionGroupMixin {

    @Shadow
    private Supplier<BlockPattern> patternFactory;

    @Shadow
    private Supplier<List<MultiblockShapeInfo>> shapes;

    /**
     * @author ae2isallyouneed
     * @reason Grouped-repetition page ordering (see class doc); otherwise a verbatim port.
     */
    @Overwrite
    public List<MultiblockShapeInfo> getMatchingShapes() {
        var designs = shapes.get();
        if (!designs.isEmpty()) return designs;
        BlockPattern pattern = patternFactory.get();
        return groupedDFS(pattern, new ArrayList<>(), new int[pattern.aisleRepetitions.length], 0);
    }

    /**
     * Enumerates the step repeats. A step covers [getGroupSize] aisles (1 for singles), so the
     * recursion advances by the group size; interior aisles of a group are never visited.
     */
    @Unique
    private List<MultiblockShapeInfo> groupedDFS(BlockPattern pattern, List<MultiblockShapeInfo> pages,
                                                 int[] repetition, int stepAisle) {
        if (stepAisle >= pattern.aisleRepetitions.length) {
            pages.add(new MultiblockShapeInfo(pattern.getPreview(repetition)));
        } else {
            int groupSize = pattern instanceof IGroupedBlockPattern grouped ? grouped.getGroupSize(stepAisle) : 1;
            if (groupSize <= 0) groupSize = 1;
            int min = pattern.aisleRepetitions[stepAisle][0];
            int max = pattern.aisleRepetitions[stepAisle][1];
            if (groupSize > 1 && min == 0) {
                for (int i = 1; i <= max; i++) {
                    repetition[stepAisle] = i;
                    groupedDFS(pattern, pages, repetition, stepAisle + groupSize);
                }
                repetition[stepAisle] = 0;
                groupedDFS(pattern, pages, repetition, stepAisle + groupSize);
            } else {
                for (int i = min; i <= max; i++) {
                    repetition[stepAisle] = i;
                    groupedDFS(pattern, pages, repetition, stepAisle + groupSize);
                }
            }
        }
        return pages;
    }
}
