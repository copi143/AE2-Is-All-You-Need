package allyouneed.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Mixin to add a machine installation slot to the Molecular Assembler and support machine-specific patterns.
 * Uses official AE2 class and method names (Mojang mappings).
 */
@Mixin(MolecularAssemblerBlockEntity.class)
public abstract class MolecularAssemblerBlockEntityMixin {

    @Final
    @Shadow(remap = false)
    private AppEngInternalInventory gridInv;

    @Final
    @Shadow(remap = false)
    private AppEngInternalInventory patternInv;

    @Shadow(remap = false)
    private double progress;

    @Shadow(remap = false)
    private boolean forcePlan;

    @Shadow(remap = false)
    private ItemStack myPattern;

    @Shadow(remap = false)
    private appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern myPlan;

    @Shadow(remap = false)
    private boolean isAwake;

    @Shadow(remap = false)
    private Direction pushDirection;

    @Unique
    private static Field allyouneed$upgradesField;

    static {
        try {
            allyouneed$upgradesField = MolecularAssemblerBlockEntity.class.getDeclaredField("upgrades");
            allyouneed$upgradesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            allyouneed$upgradesField = null;
        }
    }

    @Unique
    private AppEngInternalInventory allyouneed$machineInv;

    @Unique
    private MolecularAssemblerBlockEntity allyouneed$self() {
        return (MolecularAssemblerBlockEntity) (Object) this;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void allyouneed$init(BlockEntityType<?> blockEntityType, net.minecraft.core.BlockPos pos, BlockState blockState, CallbackInfo ci) {
        this.allyouneed$machineInv = new AppEngInternalInventory(allyouneed$self(), 1, 1);
    }

    @Unique
    private static final net.minecraft.resources.ResourceLocation MACHINE_INV_ID =
            new net.minecraft.resources.ResourceLocation("ae2isallyouneed", "ma_machine");

    @Inject(method = "getSubInventory", at = @At("HEAD"), cancellable = true, remap = false)
    private void allyouneed$getMachineSubInv(net.minecraft.resources.ResourceLocation id, CallbackInfoReturnable<appeng.api.inventories.InternalInventory> cir) {
        if (MACHINE_INV_ID.equals(id) && this.allyouneed$machineInv != null) {
            cir.setReturnValue(this.allyouneed$machineInv);
        }
    }

    // Accept machine patterns when the correct machine is installed in the slot
    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, remap = false)
    private void allyouneed$pushMachinePattern(IPatternDetails details, KeyCounter[] inputs, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (!this.myPattern.isEmpty()) return;

        boolean empty = this.gridInv.isEmpty() && this.patternInv.isEmpty();

        if (empty && details instanceof allyouneed.pattern.machine.AEMachinePattern mp) {
            ItemStack installed = allyouneed$getInstalledMachine();
            if (!installed.isEmpty() && installed.getItem() == mp.getMachineType().getMachineItem().get()) {
                this.forcePlan = true;
                this.pushDirection = dir;
                this.myPattern = details.getDefinition().toStack();

                // Store inputs into the buffer (0-8)
                int s = 0;
                for (KeyCounter list : inputs) {
                    for (var e : list) {
                        if (e.getLongValue() <= 0) continue;
                        if (e.getKey() instanceof AEItemKey ik && s < 9) {
                            this.gridInv.setItemDirect(s, ik.toStack((int) e.getLongValue()));
                            s++;
                            if (s >= 9) break;
                        }
                    }
                    if (s >= 9) break;
                }

        // For non-crafting machines we don't set myPlan to an assembler adapter (we drive manually in tick)
        if (mp.getMachineType().getEncodingMode() == allyouneed.api.machine.MachineEncodingMode.CRAFTING_GRID) {
            var adapter = mp.asAssemblerPatternOrNull();
            this.myPlan = adapter;
        } else {
            this.myPlan = null; // processing style
        }

                allyouneed$updateAwake();
                allyouneed$self().saveChanges();
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
        }
    }

    @Unique
    private ItemStack allyouneed$getInstalledMachine() {
        if (this.allyouneed$machineInv == null) return ItemStack.EMPTY;
        return this.allyouneed$machineInv.getStackInSlot(0);
    }

    @Unique
    private void allyouneed$updateAwake() {
        boolean had = this.isAwake;
        boolean now = (this.myPlan != null || this.myPattern != null) && !this.gridInv.isEmpty();
        this.isAwake = now;
        if (had != now) {
            var self = allyouneed$self();
            self.getMainNode().ifPresent((g, n) -> {
                if (now) g.getTickManager().wakeDevice(n);
                else g.getTickManager().sleepDevice(n);
            });
        }
    }

    // Persist the machine item
    // Use remap=false because we are targeting AE2's deobfuscated method names directly
    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void allyouneed$saveMachine(CompoundTag tag, CallbackInfo ci) {
        if (allyouneed$machineInv != null) {
            ItemStack m = allyouneed$machineInv.getStackInSlot(0);
            if (!m.isEmpty()) {
                CompoundTag mt = new CompoundTag();
                m.save(mt);
                tag.put("ae2ian_machine", mt);
            }
        }
    }

    @Inject(method = "loadTag", at = @At("TAIL"), remap = false)
    private void allyouneed$loadMachine(CompoundTag tag, CallbackInfo ci) {
        if (allyouneed$machineInv != null && tag.contains("ae2ian_machine")) {
            allyouneed$machineInv.setItemDirect(0, ItemStack.of(tag.getCompound("ae2ian_machine")));
        }
    }

    // Drive processing for machine patterns that are not crafting-grid style
    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true, remap = false)
    private void allyouneed$driveMachineProcessing(IGridNode node, int ticksSinceLastCall, CallbackInfoReturnable<appeng.api.networking.ticking.TickRateModulation> cir) {
        MolecularAssemblerBlockEntity self = allyouneed$self();

        ItemStack currentPat = this.myPattern;
        if (currentPat.isEmpty()) return;

        if (!(currentPat.getItem() instanceof allyouneed.pattern.machine.MachinePatternItem)) return;

        IPatternDetails decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(currentPat, self.getLevel());
        if (!(decoded instanceof allyouneed.pattern.machine.AEMachinePattern mp)) return;

        // Only handle PROCESSING_SLOTS here; CRAFTING_GRID should be handled by original myPlan path
        if (mp.getMachineType().getEncodingMode() == allyouneed.api.machine.MachineEncodingMode.CRAFTING_GRID) {
            return;
        }

        // Speed calculation (same table as original)
        int speed = 10;
        int cards = 0;
        try {
            if (allyouneed$upgradesField != null) {
                var up = (appeng.api.upgrades.IUpgradeInventory) allyouneed$upgradesField.get(this);
                cards = up.getInstalledUpgrades(AEItems.SPEED_CARD);
            }
        } catch (Exception ignored) {}

        double tax = 1.0;
        switch (cards) {
            case 0 -> { speed = 10; tax = 1.0; }
            case 1 -> { speed = 13; tax = 1.3; }
            case 2 -> { speed = 17; tax = 1.7; }
            case 3 -> { speed = 20; tax = 2.0; }
            case 4 -> { speed = 25; tax = 2.5; }
            case 5 -> { speed = 50; tax = 5.0; }
        }

        var main = self.getMainNode();
        if (main.getGrid() != null) {
            int gained = (int) (main.getGrid().getEnergyService()
                    .extractAEPower(ticksSinceLastCall * speed * tax,
                            appeng.api.config.Actionable.MODULATE,
                            appeng.api.config.PowerMultiplier.CONFIG) / tax);
            this.progress += gained;
        }

        if (this.progress >= 100) {
            this.progress = 0;

            // Emit primary output
            if (mp.getOutputs().length > 0) {
                GenericStack out = mp.getOutputs()[0];
                if (out.what() instanceof AEItemKey key) {
                    this.gridInv.setItemDirect(9, key.toStack((int) out.amount()));
                }
            }

            // Consume a set of inputs (best effort: clear first filled slot)
            for (int i = 0; i < 9; i++) {
                if (!this.gridInv.getStackInSlot(i).isEmpty()) {
                    this.gridInv.setItemDirect(i, ItemStack.EMPTY);
                    break;
                }
            }

            if (this.patternInv.isEmpty()) {
                this.forcePlan = false;
                this.myPlan = null;
                this.myPattern = ItemStack.EMPTY;
                this.pushDirection = null;
            }

            self.saveChanges();
            allyouneed$updateAwake();
            cir.setReturnValue(this.isAwake ? appeng.api.networking.ticking.TickRateModulation.IDLE : appeng.api.networking.ticking.TickRateModulation.SLEEP);
        } else {
            cir.setReturnValue(appeng.api.networking.ticking.TickRateModulation.FASTER);
        }
    }

    @Shadow(remap = false)
    private void updateSleepiness() { /* shadowed for compilation only */ }
}
