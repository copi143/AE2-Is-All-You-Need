package allyouneed.mixin;

import allyouneed.client.group.CreativeTabGroup;
import allyouneed.client.group.CreativeTabGroupRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenSidebarMixin {

    @Unique private static final int SIDEBAR_WIDTH = 80;
    @Unique private static final int ENTRY_HEIGHT = 22;
    @Unique private static final int ENTRY_PADDING = 1;
    @Unique private static final int SIDEBAR_PADDING = 2;
    @Unique private static final int ICON_SIZE = 16;
    @Unique private static final int ICON_X = 3;
    @Unique private static final int TEXT_X = 22;
    @Unique private static final int TEXT_Y_OFFSET = 5;
    @Unique private static final int BG_COLOR = 0xC0101010;
    @Unique private static final int BORDER_COLOR = 0xFF555555;
    @Unique private static final int SELECTED_BG_COLOR = 0x80444444;
    @Unique private static final int HOVER_BG_COLOR = 0x40333333;
    @Unique private static final int IMAGE_WIDTH = 195;
    @Unique private static final int IMAGE_HEIGHT = 136;

    @Unique private int scrollOffset = 0;
    @Unique private List<CreativeTabGroup> currentGroupList = List.of();

    @Unique
    private static int screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Unique
    private static int screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Unique
    private static int guiLeft() {
        return (screenWidth() - IMAGE_WIDTH) / 2;
    }

    @Unique
    private static int guiTop() {
        return (screenHeight() - IMAGE_HEIGHT) / 2;
    }

    @Unique
    private static boolean isTabInSelectedGroup(CreativeModeTab tab) {
        CreativeTabGroup selected = CreativeTabGroupRegistry.INSTANCE.getSelectedGroup();
        ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (tabId == null) return false;

        ResourceLocation ae2Id = CreativeTabGroupRegistry.INSTANCE.getAe2GroupId();
        ResourceLocation allId = CreativeTabGroupRegistry.INSTANCE.getAllGroup().getId();

        if (selected.getId().equals(allId)) {
            // ALL：显示所有 tab
            return true;
        }

        if (selected.getId().equals(ae2Id)) {
            // AE2 分类：只显示 ae2 命名空间（包括 main 和 facades）
            return "ae2".equals(tabId.getNamespace());
        }

        // 其他自定义组：只显示显式加进来的 tab
        return selected.getTabIds().contains(tabId);
    }

    // --- Tab rendering filter ---

    @Inject(method = "renderTabButton", at = @At("HEAD"), cancellable = true)
    private void hideNonGroupTab(GuiGraphics guiGraphics, CreativeModeTab tab, CallbackInfo ci) {
        if (!isTabInSelectedGroup(tab)) {
            ci.cancel();
        }
    }

    // --- Tab click filter ---

    @Inject(method = "checkTabClicked", at = @At("RETURN"), cancellable = true)
    private void filterTabClick(CreativeModeTab tab, double relativeMouseX, double relativeMouseY, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !isTabInSelectedGroup(tab)) {
            cir.setReturnValue(false);
        }
    }

    // --- Tab hover filter ---

    @Inject(method = "checkTabHovering", at = @At("RETURN"), cancellable = true)
    private void filterTabHover(GuiGraphics guiGraphics, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !isTabInSelectedGroup(tab)) {
            cir.setReturnValue(false);
        }
    }

    // --- Init ---

    @Inject(method = "init", at = @At("TAIL"))
    private void initSidebar(CallbackInfo ci) {
        currentGroupList = CreativeTabGroupRegistry.INSTANCE.getGroupList();
        scrollOffset = 0;
    }

    // --- Render ---

    @Inject(method = "render", at = @At("TAIL"))
    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (currentGroupList.isEmpty()) return;

        int left = guiLeft();
        int top = guiTop();
        int sidebarX = left - SIDEBAR_WIDTH - SIDEBAR_PADDING;
        int sidebarY = top;
        int contentX = sidebarX + SIDEBAR_PADDING;
        int contentY = sidebarY + SIDEBAR_PADDING;
        int usableHeight = IMAGE_HEIGHT - SIDEBAR_PADDING * 2;

        graphics.fill(sidebarX, sidebarY, sidebarX + SIDEBAR_WIDTH, sidebarY + IMAGE_HEIGHT, BG_COLOR);
        graphics.renderOutline(sidebarX, sidebarY, SIDEBAR_WIDTH, IMAGE_HEIGHT, BORDER_COLOR);

        int maxEntries = usableHeight / (ENTRY_HEIGHT + ENTRY_PADDING);
        int maxScroll = Math.max(0, currentGroupList.size() - maxEntries);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        int startIndex = scrollOffset;
        int endIndex = Math.min(currentGroupList.size(), startIndex + maxEntries);

        ResourceLocation selectedId = CreativeTabGroupRegistry.INSTANCE.getSelectedGroupId();
        Font font = Minecraft.getInstance().font;

        for (int i = startIndex; i < endIndex; i++) {
            CreativeTabGroup group = currentGroupList.get(i);
            int entryY = contentY + (i - startIndex) * (ENTRY_HEIGHT + ENTRY_PADDING);
            int entryRight = contentX + SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;

            boolean isSelected = group.getId().equals(selectedId);
            boolean isHovered = mouseX >= contentX && mouseX < entryRight
                && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;

            if (isSelected) {
                graphics.fill(contentX, entryY, entryRight, entryY + ENTRY_HEIGHT, SELECTED_BG_COLOR);
            } else if (isHovered) {
                graphics.fill(contentX, entryY, entryRight, entryY + ENTRY_HEIGHT, HOVER_BG_COLOR);
            }

            ItemStack icon = group.getIcon().get();
            if (!icon.isEmpty()) {
                graphics.renderFakeItem(icon, contentX + ICON_X, entryY + (ENTRY_HEIGHT - ICON_SIZE) / 2);
            }

            String text = group.getDisplayName().getString();
            int maxTextWidth = SIDEBAR_WIDTH - TEXT_X - 4;
            if (font.width(text) > maxTextWidth) {
                text = font.plainSubstrByWidth(text + "...", maxTextWidth);
            }
            int textColor = isSelected ? 0xFFFFFFE0 : (isHovered ? 0xFFFFFFFF : 0xFFAAAAAA);
            graphics.drawString(font, text, contentX + TEXT_X, entryY + TEXT_Y_OFFSET, textColor);
        }

        if (scrollOffset > 0) {
            drawTriangleUp(graphics, sidebarX + SIDEBAR_WIDTH / 2 - 4, sidebarY + 3, 8, 4, 0xFFAAAAAA);
        }
        if (scrollOffset < maxScroll) {
            drawTriangleDown(graphics, sidebarX + SIDEBAR_WIDTH / 2 - 4, sidebarY + IMAGE_HEIGHT - 7, 8, 4, 0xFFAAAAAA);
        }
    }

    @Unique
    private static void drawTriangleUp(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            int l = x + (w * row / (h * 2));
            int r = x + w - (w * row / (h * 2));
            g.fill(l, y + h - 1 - row, r, y + h - row, color);
        }
    }

    @Unique
    private static void drawTriangleDown(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            int l = x + (w * row / (h * 2));
            int r = x + w - (w * row / (h * 2));
            g.fill(l, y + row, r, y + row + 1, color);
        }
    }

    // --- Mouse ---

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void handleSidebarClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        int left = guiLeft();
        int top = guiTop();
        int sidebarX = left - SIDEBAR_WIDTH - SIDEBAR_PADDING;
        int sidebarY = top + SIDEBAR_PADDING;

        if (mouseX < sidebarX || mouseX > sidebarX + SIDEBAR_WIDTH
            || mouseY < top || mouseY > top + IMAGE_HEIGHT) {
            return;
        }

        int usableHeight = IMAGE_HEIGHT - SIDEBAR_PADDING * 2;
        int relativeY = (int) mouseY - sidebarY;
        int clickedIndex = relativeY / (ENTRY_HEIGHT + ENTRY_PADDING) + scrollOffset;

        if (clickedIndex >= 0 && clickedIndex < currentGroupList.size()) {
            CreativeTabGroup clickedGroup = currentGroupList.get(clickedIndex);
            CreativeTabGroupRegistry.INSTANCE.setSelectedGroup(clickedGroup.getId());
            ((Screen) (Object) this).init(Minecraft.getInstance(), screenWidth(), screenHeight());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void handleSidebarScroll(double mouseX, double mouseY, double scrollDelta, CallbackInfoReturnable<Boolean> cir) {
        int left = guiLeft();
        int top = guiTop();
        int sidebarX = left - SIDEBAR_WIDTH - SIDEBAR_PADDING;

        if (mouseX < sidebarX || mouseX > sidebarX + SIDEBAR_WIDTH
            || mouseY < top || mouseY > top + IMAGE_HEIGHT) {
            return;
        }

        int usableHeight = IMAGE_HEIGHT - SIDEBAR_PADDING * 2;
        int maxEntries = usableHeight / (ENTRY_HEIGHT + ENTRY_PADDING);
        int maxScroll = Math.max(0, currentGroupList.size() - maxEntries);

        scrollOffset -= (int) Math.signum(scrollDelta);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        cir.setReturnValue(true);
    }
}
