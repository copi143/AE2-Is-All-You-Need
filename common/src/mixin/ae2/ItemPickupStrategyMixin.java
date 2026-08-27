package allyouneed.mixin.ae2;

import allyouneed.logic.aekey.XpKey;
import appeng.api.behaviors.PickupSink;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.parts.automation.ItemPickupStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgeBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 方块破坏时将原版应掉落的经验一并吸收为 {@link XpKey} (level=1) 入网。
 * 使用默认存储成本（LevelOnlyKey amountPerByte=8），由 sink 内部的
 * StorageHelper.poweredInsert 自动扣电。
 */
@Mixin(value = ItemPickupStrategy.class, remap = false)
public abstract class ItemPickupStrategyMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private BlockPos pos;

    @Shadow
    @Final
    private Map<Enchantment, Integer> enchantments;

    @Inject(method = "completePickup", at = @At("TAIL"))
    private void allyouneed$storeBlockExp(IEnergySource energySource, PickupSink sink, List items, float requiredPower, BlockState blockState, CallbackInfo ci) {
        if (level == null || pos == null || sink == null || blockState == null) return;

        // 计算应掉落经验，尊重时运/精准
        int fortune = 0;
        int silkTouch = 0;
        if (enchantments != null) {
            fortune = enchantments.getOrDefault(Enchantments.BLOCK_FORTUNE, 0);
            if (enchantments.containsKey(Enchantments.SILK_TOUCH)) {
                silkTouch = 1;
            }
        }

        Block block = blockState.getBlock();
        int exp;
        try {
            exp = ((IForgeBlock) block).getExpDrop(blockState, level, level.getRandom(), pos, fortune, silkTouch);
        } catch (Throwable t) {
            // 兼容性兜底：若 getExpDrop 抛异常则不吸收
            return;
        }

        if (exp <= 0) return;

        XpKey key = new XpKey(1);
        long inserted = sink.insert(key, exp, Actionable.MODULATE);
        if (inserted < exp) {
            int remaining = exp - (int) inserted;
            if (remaining > 0) {
                // 剩余经验以原版方式掉落为经验球，避免丢失（Block.popExperience 为 protected）
                if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                    ExperienceOrb.award(level, Vec3.atCenterOf(pos), remaining);
                }
            }
        }
    }
}
