package allyouneed.mixin.client;

import allyouneed.util.bigint.BigAmounts;
import allyouneed.util.CommonKt;
import allyouneed.util.SiFormat;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.menu.me.common.GridInventoryEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;

@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class MEStorageScreenMixin {

    private static String formatFullAmount(AEKey what, BigInteger amount) {
        StringBuilder result = new StringBuilder();
        int perUnit = what.getAmountPerUnit();
        if (perUnit > 1) {
            BigDecimal units = new BigDecimal(amount).divide(BigDecimal.valueOf(perUnit), 3, RoundingMode.DOWN);
            result.append(NumberFormat.getNumberInstance().format(units));
        } else {
            result.append(NumberFormat.getNumberInstance().format(amount));
        }
        String unit = what.getUnitSymbol();
        if (unit != null) {
            result.append(' ').append(unit);
        }
        return result.toString();
    }

    @Redirect(method = "renderSlot", at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;"))
    private String allyouneed$formatSlotAmount(AEKey key, long amount, AmountFormat format, GuiGraphics guiGraphics, Slot s) {
        if (s instanceof RepoSlot repoSlot) {
            GridInventoryEntry entry = repoSlot.getEntry();
            if (entry != null) {
                BigInteger big = BigAmounts.getEntryAmount(entry);
                int width = format == AmountFormat.SLOT_LARGE_FONT ? 3 : 4;
                if (key.getAmountPerUnit() > 1) {
                    BigInteger units = big.divide(BigInteger.valueOf(key.getAmountPerUnit()));
                    return SiFormat.format(units, width);
                }
                return SiFormat.format(big, width);
            }
        }
        return key.formatAmount(amount, format);
    }

    @Redirect(method = "renderGridInventoryEntryTooltip", at = @At(value = "INVOKE", target = "Lappeng/core/localization/Tooltips;shouldShowAmountTooltip(Lappeng/api/stacks/AEKey;J)Z"))
    private boolean allyouneed$shouldShowBigTooltip(AEKey what, long amount, GuiGraphics guiGraphics, GridInventoryEntry entry, int x, int y) {
        BigInteger big = BigAmounts.getEntryAmount(entry);
        if (big.bitLength() > 14) { // > ~16384 always show
            return true;
        }
        return Tooltips.shouldShowAmountTooltip(what, CommonKt.saturateToLong(big));
    }

    @Redirect(method = "renderGridInventoryEntryTooltip", at = @At(value = "INVOKE", target = "Lappeng/core/localization/Tooltips;getAmountTooltip(Lappeng/core/localization/ButtonToolTips;Lappeng/api/stacks/AEKey;J)Lnet/minecraft/network/chat/Component;"))
    private Component allyouneed$bigAmountTooltip(ButtonToolTips baseText, AEKey what, long amount, GuiGraphics guiGraphics, GridInventoryEntry entry, int x, int y) {
        BigInteger big = BigAmounts.getEntryAmount(entry);
        String amountText = formatFullAmount(what, big);
        return baseText.text(amountText).withStyle(Tooltips.MUTED_COLOR);
    }
}
