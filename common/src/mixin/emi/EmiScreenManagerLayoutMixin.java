package allyouneed.mixin.emi;

import allyouneed.client.integration.emi.fold.EmiFoldUi;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.List;
import java.util.function.Supplier;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerLayoutMixin {
    @Redirect(method = "createScreenSpace", at = @At(value = "NEW", target = "dev/emi/emi/screen/EmiScreenManager$ScreenSpace", ordinal = 0), remap = false)
    private static EmiScreenManager.ScreenSpace ae2inya$navigationSpace(
            int tx, int ty, int tw, int th, boolean rtl, List<Bounds> exclusions,
            Supplier<SidebarType> type, boolean search,
            EmiScreenManager.SidebarPanel panel, Screen screen, List<Bounds> originalExclusions,
            boolean originalRtl, Bounds bounds, SidebarSettings settings) {
        return EmiFoldUi.createSpace(tx, ty, tw, th, rtl, exclusions, type, search, panel);
    }
}
