package allyouneed.mixin.gtceu;

import allyouneed.gt.IGroupedBlockPattern;
import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Injects "group repetition" into the GTCEu {@link BlockPattern}: a step may cover {@code G}
 * consecutive aisles (the async extension bay) that repeat as a unit instead of a single slice.
 *
 * <p>The group metadata lives in a {@code @Unique int[] groupSizes} field exposed through the
 * {@link IGroupedBlockPattern} interface. The first aisle of a group stores {@code G}, group
 * interior aisles default to 1 and are only ever reached as part of their group step.
 *
 * <p>All three overwritten methods are line-for-line ports of the GTCEu 7.5.3 originals with the
 * aisle loop restructured around steps ({@code c += groupSize}):
 * <ul>
 *   <li>{@link #checkPatternAt(MultiblockState, BlockPos, Direction, Direction, boolean, boolean)}
 *       matches each step at {@code groupSize} consecutive z slices; every cell-level concern
 *       (findFirstAisle sliding/retreat, layer limits, pos cache, parts sharing, io/vaBlocks) is
 *       preserved verbatim.</li>
 *   <li>{@link #getPreview(int[])} renders {@code groupSize} slices per group repetition.</li>
 *   <li>{@link #autoBuild(Player, MultiblockState)} places {@code max(1, min)} group repetitions
 *       (forming still allows 0).</li>
 * </ul>
 */
@Mixin(value = BlockPattern.class, remap = false)
public abstract class BlockPatternGroupedMixin implements IGroupedBlockPattern {

    @Shadow
    public int[][] aisleRepetitions;
    @Shadow
    protected TraceabilityPredicate[][][] blockMatches;
    @Shadow
    protected int fingerLength;
    @Shadow
    protected int thumbLength;
    @Shadow
    protected int palmLength;
    @Shadow
    protected int[] centerOffset;
    @Shadow
    protected int[] formedRepetitionCount;

    @Shadow
    protected abstract BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing,
                                                         Direction upwardsFacing, boolean isFlipped);

    @Shadow
    private void resetFacing(BlockPos pos, BlockState blockState, Direction facing,
                             BiPredicate<BlockPos, Direction> checker, Consumer<BlockState> consumer) {
    }

    @Shadow
    private static IntObjectPair<IItemHandler> getMatchStackWithHandler(List<ItemStack> candidates,
                                                                        LazyOptional<IItemHandler> cap) {
        return null;
    }

    @Unique
    private int[] groupSizes;

    @Override
    public void setGroup(int aisleIndex, int groupSize) {
        if (groupSizes == null) {
            groupSizes = new int[aisleRepetitions.length];
            java.util.Arrays.fill(groupSizes, 1);
        }
        groupSizes[aisleIndex] = groupSize;
    }

    @Override
    public int getGroupSize(int aisleIndex) {
        return groupSizes == null ? 1 : groupSizes[aisleIndex];
    }

    /**
     * @author ae2isallyouneed
     * @reason Grouped-repetition pattern matching (see class doc); otherwise a verbatim port.
     */
    @Overwrite
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction frontFacing,
                                  Direction upwardsFacing, boolean isFlipped, boolean savePredicate) {
        boolean findFirstAisle = false;
        int minZ = -centerOffset[4];
        worldState.clean();
        PatternMatchContext matchContext = worldState.getMatchContext();
        Object2IntMap<SimplePredicate> globalCount = worldState.getGlobalCount();
        Object2IntMap<SimplePredicate> layerCount = worldState.getLayerCount();
        for (int c = 0, z = minZ++, r; c < this.fingerLength; ) {
            int groupSize = getGroupSize(c);
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset[3]); r++) {
                layerCount.clear();
                int sliceZ = z;
                boolean groupFailed = false;
                for (int d = 0; d < groupSize; d++) {
                    for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                        for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                            worldState.setError(null);
                            TraceabilityPredicate predicate = this.blockMatches[c + d][b][a];
                            BlockPos pos = setActualRelativeOffset(x, y, sliceZ + d, frontFacing, upwardsFacing,
                                    isFlipped).offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                            if (!worldState.update(pos, predicate)) {
                                return false;
                            }
                            if (predicate.addCache()) {
                                worldState.addPosCache(pos);
                                if (savePredicate) {
                                    matchContext.getOrCreate("predicates", HashMap::new).put(pos, predicate);
                                }
                            }
                            boolean canPartShared = true;
                            if (worldState.getTileEntity() instanceof IMachineBlockEntity machineBlockEntity &&
                                    machineBlockEntity.getMetaMachine() instanceof IMultiPart part) {
                                if (!predicate.isAny()) {
                                    if (part.isFormed() && !part.canShared() &&
                                            !part.hasController(worldState.controllerPos)) {
                                        canPartShared = false;
                                        worldState.setError(new PatternStringError("multiblocked.pattern.error.share"));
                                    } else {
                                        matchContext.getOrCreate("parts", HashSet::new).add(part);
                                    }
                                }
                            }
                            if (worldState.getBlockState().getBlock() instanceof ActiveBlock) {
                                matchContext.getOrCreate("vaBlocks", LongOpenHashSet::new)
                                        .add(worldState.getPos().asLong());
                            }
                            if (!predicate.test(worldState) || !canPartShared) { // matching failed
                                groupFailed = true;
                                break;
                            }
                            matchContext.getOrCreate("ioMap", Long2ObjectOpenHashMap::new).put(
                                    worldState.getPos().asLong(), worldState.io);
                        }
                        if (groupFailed) break;
                    }
                    if (groupFailed) break;
                }
                if (groupFailed) {
                    if (findFirstAisle) {
                        if (r < aisleRepetitions[c][0]) { // retreat to see if the first aisle can start later
                            r = c = 0;
                            z = minZ++;
                            matchContext.reset();
                            findFirstAisle = false;
                        }
                    } else {
                        z++; // continue searching for the first aisle
                    }
                    continue loop;
                }
                findFirstAisle = true;
                z += groupSize;

                // Check layer-local matcher predicate
                for (var entry : layerCount.object2IntEntrySet()) {
                    if (entry.getIntValue() < entry.getKey().minLayerCount) {
                        worldState.setError(new SinglePredicateError(entry.getKey(), 3));
                        return false;
                    }
                }
                validRepetitions++;
            }
            // Repetitions out of range
            if (r < aisleRepetitions[c][0] || worldState.hasError() || !findFirstAisle) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return false;
            }

            // finished checking the step, so store the repetitions
            for (int d = 0; d < groupSize; d++) {
                formedRepetitionCount[c + d] = validRepetitions;
            }
            c += groupSize;
        }

        // Check count matches amount
        for (var entry : globalCount.object2IntEntrySet()) {
            if (entry.getIntValue() < entry.getKey().minCount) {
                worldState.setError(new SinglePredicateError(entry.getKey(), 1));
                return false;
            }
        }

        worldState.setError(null);
        worldState.setNeededFlip(isFlipped);
        return true;
    }

    /**
     * @author ae2isallyouneed
     * @reason Grouped-repetition preview (see class doc); otherwise a verbatim port.
     */
    @Overwrite
    public BlockInfo[][][] getPreview(int[] repetition) {
        Object2IntOpenHashMap<SimplePredicate> cacheGlobal = new Object2IntOpenHashMap<>();
        Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int c = 0, x = 0; c < this.fingerLength; ) {
            int groupSize = getGroupSize(c);
            for (int r = 0; r < repetition[c]; r++) {
                for (int d = 0; d < groupSize; d++) {
                    // Checking single slice
                    Object2IntOpenHashMap<SimplePredicate> cacheLayer = new Object2IntOpenHashMap<>();
                    for (int y = 0; y < this.thumbLength; y++) {
                        for (int z = 0; z < this.palmLength; z++) {
                            TraceabilityPredicate predicate = this.blockMatches[c + d][y][z];
                            boolean find = false;
                            BlockInfo[] infos = null;
                            for (SimplePredicate limit : predicate.limited) { // check layer and previewCount
                                if (limit.minLayerCount > 0) {
                                    if (cacheLayer.getInt(limit) < limit.minLayerCount) {
                                        cacheLayer.addTo(limit, 1);
                                    } else {
                                        continue;
                                    }
                                    if (cacheGlobal.getInt(limit) < limit.previewCount) {
                                        cacheGlobal.addTo(limit, 1);
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                infos = limit.candidates == null ? null : limit.candidates.get();
                                find = true;
                                break;
                            }
                            if (!find) { // check global and previewCount
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.minCount == -1 && limit.previewCount == -1) continue;
                                    if (cacheGlobal.getInt(limit) < limit.previewCount) {
                                        cacheGlobal.addTo(limit, 1);
                                    } else if (limit.minCount > 0) {
                                        if (cacheGlobal.getInt(limit) < limit.minCount) {
                                            cacheGlobal.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    find = true;
                                    break;
                                }
                            }
                            if (!find) { // check common with previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.previewCount > 0) {
                                        if (cacheGlobal.getInt(common) < common.previewCount) {
                                            cacheGlobal.addTo(common, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    infos = common.candidates == null ? null : common.candidates.get();
                                    find = true;
                                    break;
                                }
                            }
                            if (!find) { // check without previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.previewCount == -1) {
                                        infos = common.candidates == null ? null : common.candidates.get();
                                        find = true;
                                        break;
                                    }
                                }
                            }
                            if (!find) { // check max
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.previewCount != -1) continue;
                                    if (limit.maxCount != -1 || limit.maxLayerCount != -1) {
                                        if (cacheGlobal.getOrDefault(limit, 0) < limit.maxCount) {
                                            cacheGlobal.addTo(limit, 1);
                                        } else if (cacheLayer.getOrDefault(limit, 0) < limit.maxLayerCount) {
                                            cacheLayer.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    }
                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    break;
                                }
                            }
                            BlockInfo info = infos == null || infos.length == 0 ? BlockInfo.EMPTY : infos[0];
                            BlockPos pos = setActualRelativeOffset(z, y, x, Direction.NORTH, Direction.UP, false);

                            blocks.put(pos, info);
                            minX = Math.min(pos.getX(), minX);
                            minY = Math.min(pos.getY(), minY);
                            minZ = Math.min(pos.getZ(), minZ);
                            maxX = Math.max(pos.getX(), maxX);
                            maxY = Math.max(pos.getY(), maxY);
                            maxZ = Math.max(pos.getZ(), maxZ);
                        }
                    }
                    x++;
                }
            }
            c += groupSize;
        }
        BlockInfo[][][] result = (BlockInfo[][][]) java.lang.reflect.Array.newInstance(BlockInfo.class,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        int finalMinX = minX;
        int finalMinY = minY;
        int finalMinZ = minZ;
        blocks.forEach((pos, info) -> {
            resetFacing(pos, info.getBlockState(), null, (p, f) -> {
                BlockInfo blockInfo = blocks.get(p.relative(f));
                if (blockInfo == null || blockInfo.getBlockState().getBlock() == Blocks.AIR) {
                    if (blocks.get(pos).getBlockState().getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock
                            machineBlock) {
                        if (machineBlock.newBlockEntity(BlockPos.ZERO,
                                machineBlock.defaultBlockState()) instanceof IMachineBlockEntity machineBlockEntity) {
                            var machine = machineBlockEntity.getMetaMachine();
                            if (machine instanceof IMultiController) {
                                return false;
                            } else {
                                return machine.isFacingValid(f);
                            }
                        }
                    }
                    return true;
                }
                return false;
            }, info::setBlockState);
            result[pos.getX() - finalMinX][pos.getY() - finalMinY][pos.getZ() - finalMinZ] = info;
        });
        return result;
    }

    /**
     * @author ae2isallyouneed
     * @reason Grouped-repetition auto build (see class doc); otherwise a verbatim port.
     */
    @Overwrite
    public void autoBuild(Player player, MultiblockState worldState) {
        Level world = player.level();
        int minZ = -centerOffset[4];
        worldState.clean();
        IMultiController controller = worldState.getController();
        BlockPos centerPos = controller.self().getPos();
        Direction facing = controller.self().getFrontFacing();
        Direction upwardsFacing = controller.self().getUpwardsFacing();
        boolean isFlipped = controller.self().isFlipped();
        Object2IntOpenHashMap<SimplePredicate> cacheGlobal = worldState.getGlobalCount();
        Object2IntOpenHashMap<SimplePredicate> cacheLayer = worldState.getLayerCount();
        Map<BlockPos, Object> blocks = new HashMap<>();
        Set<BlockPos> placeBlockPos = new HashSet<>();
        blocks.put(centerPos, controller);
        for (int c = 0, z = minZ++, r; c < this.fingerLength; ) {
            int groupSize = getGroupSize(c);
            int repeats = Math.max(1, aisleRepetitions[c][0]);
            for (r = 0; r < repeats; r++) {
                cacheLayer.clear();
                for (int d = 0; d < groupSize; d++) {
                    for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                        for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                            TraceabilityPredicate predicate = this.blockMatches[c + d][b][a];
                            BlockPos pos = setActualRelativeOffset(x, y, z + d, facing, upwardsFacing, isFlipped)
                                    .offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                            worldState.update(pos, predicate);
                            if (!world.isEmptyBlock(pos)) {
                                blocks.put(pos, world.getBlockState(pos));
                                for (SimplePredicate limit : predicate.limited) {
                                    limit.testLimited(worldState);
                                }
                            } else {
                                boolean find = false;
                                BlockInfo[] infos = new BlockInfo[0];
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.minLayerCount > 0) {
                                        int curr = cacheLayer.getInt(limit);
                                        if (curr < limit.minLayerCount &&
                                                (limit.maxLayerCount == -1 || curr < limit.maxLayerCount)) {
                                            cacheLayer.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    find = true;
                                    break;
                                }
                                if (!find) {
                                    for (SimplePredicate limit : predicate.limited) {
                                        if (limit.minCount > 0) {
                                            int curr = cacheGlobal.getInt(limit);
                                            if (curr < limit.minCount && (limit.maxCount == -1 || curr < limit.maxCount)) {
                                                cacheGlobal.addTo(limit, 1);
                                            } else {
                                                continue;
                                            }
                                        } else {
                                            continue;
                                        }
                                        infos = limit.candidates == null ? null : limit.candidates.get();
                                        find = true;
                                        break;
                                    }
                                }
                                if (!find) { // no limited
                                    for (SimplePredicate limit : predicate.limited) {
                                        if (limit.maxLayerCount != -1 &&
                                                cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxLayerCount) {
                                            continue;
                                        }
                                        if (limit.maxCount != -1 &&
                                                cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxCount) {
                                            continue;
                                        }
                                        cacheLayer.addTo(limit, 1);
                                        cacheGlobal.addTo(limit, 1);
                                        infos = ArrayUtils.addAll(infos,
                                                limit.candidates == null ? null : limit.candidates.get());
                                    }
                                    for (SimplePredicate common : predicate.common) {
                                        infos = ArrayUtils.addAll(infos,
                                                common.candidates == null ? null : common.candidates.get());
                                    }
                                }

                                List<ItemStack> candidates = new ArrayList<>();
                                if (infos != null) {
                                    for (BlockInfo info : infos) {
                                        if (info.getBlockState().getBlock() != Blocks.AIR) {
                                            candidates.add(info.getItemStackForm());
                                        }
                                    }
                                }

                                // check inventory
                                ItemStack found = null;
                                int foundSlot = -1;
                                IItemHandler handler = null;
                                if (!player.isCreative()) {
                                    // The common module compiles against the vanilla Player, so reach
                                    // the Forge capability provider through the interface it implements.
                                    var foundHandler = getMatchStackWithHandler(candidates,
                                            ((ICapabilityProvider) player).getCapability(ForgeCapabilities.ITEM_HANDLER));
                                    if (foundHandler != null) {
                                        foundSlot = foundHandler.firstInt();
                                        handler = foundHandler.second();
                                        found = handler.getStackInSlot(foundSlot).copy();
                                    }
                                } else {
                                    for (ItemStack candidate : candidates) {
                                        found = candidate.copy();
                                        if (!found.isEmpty() && found.getItem() instanceof BlockItem) {
                                            break;
                                        }
                                        found = null;
                                    }
                                }
                                if (found == null) continue;
                                BlockItem itemBlock = (BlockItem) found.getItem();
                                BlockPlaceContext context = new BlockPlaceContext(world, player, InteractionHand.MAIN_HAND,
                                        found, BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                                InteractionResult interactionResult = itemBlock.place(context);
                                if (interactionResult != InteractionResult.FAIL) {
                                    placeBlockPos.add(pos);
                                    if (handler != null) {
                                        handler.extractItem(foundSlot, 1, false);
                                    }
                                }
                                if (world.getBlockEntity(pos) instanceof IMachineBlockEntity machineBlockEntity) {
                                    blocks.put(pos, machineBlockEntity.getMetaMachine());
                                } else {
                                    blocks.put(pos, world.getBlockState(pos));
                                }
                            }
                        }
                    }
                }
                z += groupSize;
            }
            c += groupSize;
        }
        Direction frontFacing = controller.self().getFrontFacing();
        blocks.forEach((pos, block) -> { // adjust facing
            if (!(block instanceof IMultiController)) {
                if (block instanceof BlockState && placeBlockPos.contains(pos)) {
                    resetFacing(pos, (BlockState) block, frontFacing, (p, f) -> {
                        Object object = blocks.get(p.relative(f));
                        return object == null ||
                                (object instanceof BlockState && ((BlockState) object).getBlock() == Blocks.AIR);
                    }, state -> world.setBlock(pos, state, 3));
                } else if (block instanceof MetaMachine machine) {
                    resetFacing(pos, machine.getBlockState(), frontFacing, (p, f) -> {
                        Object object = blocks.get(p.relative(f));
                        if (object == null || (object instanceof BlockState blockState && blockState.isAir())) {
                            return machine.isFacingValid(f);
                        }
                        return false;
                    }, state -> world.setBlock(pos, state, 3));
                }
            }
        });
    }
}
