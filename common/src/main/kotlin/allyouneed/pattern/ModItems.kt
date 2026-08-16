package allyouneed.pattern

import allyouneed.cell.creative.CreativeMeCellItem
import allyouneed.cell.dimensional.DimensionalCellItem
import allyouneed.parts.p2p.EntityP2PTunnelPart
import allyouneed.pattern.adaptive.AdaptivePatternItem
import allyouneed.pattern.machine.MachinePatternItem
import allyouneed.pattern.pseudo.PseudoPatternItem
import allyouneed.pattern.term.UnifiedPatternEncodingTermPart
import allyouneed.util.rl
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import appeng.items.parts.PartItem
import net.minecraft.world.item.Item

object ModItems {
    val PSEUDO_PATTERN: PseudoPatternItem by lazy {
        PseudoPatternItem(Item.Properties().stacksTo(1))
    }

    val ADAPTIVE_PATTERN: AdaptivePatternItem by lazy {
        AdaptivePatternItem(Item.Properties().stacksTo(1))
    }

    val MACHINE_PATTERN: MachinePatternItem by lazy {
        MachinePatternItem(Item.Properties().stacksTo(1))
    }

    val ENTITY_P2P_TUNNEL: PartItem<EntityP2PTunnelPart> by lazy {
        PartItem(Item.Properties().stacksTo(64), EntityP2PTunnelPart::class.java) { EntityP2PTunnelPart(it) }
    }

    val PATTERN_ENCODING_TERMINAL: PartItem<UnifiedPatternEncodingTermPart> by lazy {
        PartItem(
            Item.Properties().stacksTo(64),
            UnifiedPatternEncodingTermPart::class.java,
        ) { UnifiedPatternEncodingTermPart(it) }
    }

    val PATTERN_ENCODING_TERMINAL_DEF: ItemDefinition<PartItem<UnifiedPatternEncodingTermPart>> by lazy {
        ItemDefinition(
            "ME Pattern Encoding Terminal",
            "pattern_encoding_terminal".rl,
            PATTERN_ENCODING_TERMINAL,
        ).also { MainCreativeTab.add(it) }
    }

    val CREATIVE_ME_CELL: CreativeMeCellItem by lazy {
        CreativeMeCellItem.create()
    }

    val DIMENSIONAL_CELL: DimensionalCellItem by lazy {
        DimensionalCellItem.create()
    }
}
