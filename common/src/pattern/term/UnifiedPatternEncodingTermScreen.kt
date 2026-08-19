package allyouneed.pattern.term

import appeng.client.gui.WidgetContainer
import appeng.client.gui.me.items.PatternEncodingTermScreen
import appeng.client.gui.style.Blitter
import appeng.client.gui.style.ScreenStyle
import appeng.client.gui.widgets.TabButton
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class UnifiedPatternEncodingTermScreen(
    menu: UnifiedPatternEncodingTermMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle,
) : PatternEncodingTermScreen<UnifiedPatternEncodingTermMenu>(menu, playerInventory, title, style) {

    private val kindTabs: Map<EncodingKind, TabButton> = EncodingKind.entries.associateWith { kind ->
        val tab = TabButton(kind.icon(), kind.tooltip()) { menu.setKind(kind) }
        tab.setStyle(TabButton.Style.HORIZONTAL)
        widgets.add("kindTabButton${kind.ordinal}", tab)
        tab
    }

    private val machineSlot = MachineSlotWidget(menu).also { widgets.add("machineSlot", it) }

    @Suppress("UsePropertyAccessSyntax")
    private val probabilityField = widgets.addTextField("adaptiveProbability").apply {
        setMaxLength(8)
        message = Component.translatable("gui.ae2isallyouneed.adaptive_probability")
        value = formatProbability(menu.probability)
        setResponder { value -> parseDouble(value)?.let { menu.setProbability(it) } }
    }

    @Suppress("UsePropertyAccessSyntax")
    private val timeoutField = widgets.addTextField("adaptiveTimeout").apply {
        setMaxLength(5)
        message = Component.translatable("gui.ae2isallyouneed.adaptive_timeout")
        value = menu.timeout.toString()
        setResponder { value -> parseInt(value)?.let { menu.setTimeout(it) } }
    }

    override fun updateBeforeRender() {
        super.updateBeforeRender()
        hideAe2ModeTabs()

        for ((kind, tab) in kindTabs) {
            tab.isSelected = menu.kind == kind
        }

        val showMachine = menu.kind == EncodingKind.MACHINE
        machineSlot.visible = showMachine
        if (showMachine) {
            setTextContent("machine_slot_label", menu.selectedMachine?.name ?: Component.empty())
        } else {
            setTextContent("machine_slot_label", Component.empty())
        }

        val showProbability = menu.kind == EncodingKind.PROBABILITY
        probabilityField.visible = showProbability
        timeoutField.visible = showProbability
        if (showProbability) {
            if (!probabilityField.isFocused) {
                val current = formatProbability(menu.probability)
                if (current != probabilityField.value) probabilityField.value = current
            }
            if (!timeoutField.isFocused) {
                val current = menu.timeout.toString()
                if (current != timeoutField.value) timeoutField.value = current
            }
        }

        val hideOutputs = menu.kind == EncodingKind.MACHINE || menu.kind == EncodingKind.PSEUDO
        if (hideOutputs) {
            for (slot in menu.processingOutputSlots) {
                slot.isActive = false
            }
        }
        widgetById("processingCycleOutput")?.visible = !hideOutputs
    }

    private fun hideAe2ModeTabs() {
        for (i in 0..3) {
            widgetById("modeTabButton$i")?.visible = false
        }
    }

    private fun widgetById(id: String): AbstractWidget? {
        val field = WidgetContainer::class.java.getDeclaredField("widgets")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(widgets) as Map<String, AbstractWidget>
        return map[id]
    }

    companion object {
        private fun formatProbability(p: Double): String = "%.4f".format(p)

        private fun parseDouble(s: String): Double? = try {
            s.trim().toDouble().takeIf { it in 0.01..1.0 }
        } catch (_: NumberFormatException) {
            null
        }

        private fun parseInt(s: String): Int? = try {
            s.trim().toInt().takeIf { it in 1..3600 }
        } catch (_: NumberFormatException) {
            null
        }

        private fun EncodingKind.icon(): ItemStack = ItemStack(
            when (this) {
                EncodingKind.MACHINE -> Items.CRAFTING_TABLE
                EncodingKind.PROCESSING -> Items.FURNACE
                EncodingKind.PROBABILITY -> Items.REDSTONE
                EncodingKind.PSEUDO -> Items.PAPER
            },
        )

        private fun EncodingKind.tooltip(): Component = Component.translatable(
            "gui.ae2isallyouneed.encoding." + name.lowercase(),
        )
    }
}

private class MachineSlotWidget(
    private val menu: UnifiedPatternEncodingTermMenu,
) : AbstractWidget(0, 0, 18, 18, Component.empty()) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        Blitter.texture(MACHINE_SLOT_TEXTURE).dest(x, y).blit(guiGraphics)
        val machine = menu.selectedMachine
        if (machine != null) {
            guiGraphics.renderItem(machine.icon, x + 1, y + 1)
        }
        if (isHovered) {
            val lines = mutableListOf<Component>()
            lines += machine?.name ?: Component.translatable("gui.ae2isallyouneed.machine_slot_no_machine")
            lines += Component.translatable("gui.ae2isallyouneed.machine_slot_hint")
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY)
        }
    }

    override fun updateWidgetNarration(narration: NarrationElementOutput) {
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isHovered) {
            menu.cycleMachine()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    companion object {
        private val MACHINE_SLOT_TEXTURE = ResourceLocation("ae2isallyouneed", "textures/guis/machine_slot.png")
    }
}
