package allyouneed.forge.botania

import allyouneed.logic.aekey.ManaKey
import allyouneed.logic.aekey.ManaType
import allyouneed.util.logger
import appeng.api.behaviors.StackExportStrategy
import appeng.api.behaviors.StackImportStrategy
import appeng.api.behaviors.StackTransferContext
import appeng.api.config.Actionable
import appeng.api.stacks.AEKey
import appeng.util.BlockApiCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import vazkii.botania.api.BotaniaForgeCapabilities
import vazkii.botania.api.mana.ManaPool
import vazkii.botania.api.mana.ManaReceiver

/**
 * Botania mana integration for the vanilla ME import/export buses.
 *
 * The bus face pointing at a [ManaReceiver] (mana pool, mana flux field, ...) becomes a mana
 * port: the import bus pulls mana into the network as [ManaKey], the export bus pushes stored
 * mana back out. Unit conversion: 1 Mana = 5 AM = 5 × [ManaKey.MANA_GRANULARITY] internal
 * units ([ManaType.BotaniaMana.manaPerAM] = 0.2, see [allyouneed.util.interfaces.PlatformHelper]).
 *
 * Transfer accounting is loss-less on both sides:
 * - import clamps the withdrawal against the network's simulated insert;
 * - export measures the receiver's actual gain (`currentMana` before/after) and only extracts
 *   that amount from the network, so receivers that silently clamp (or convert) never lose mana.
 */
object BotaniaManaCompat {
    /** Mana moved per bus operation (≈ 6 standard bursts). */
    internal const val MANA_PER_OPERATION = 1000

    /** Upper bound for a single injection step into non-pool receivers. */
    internal const val MAX_INJECTION_CHUNK = MANA_PER_OPERATION

    fun register() {
        StackImportStrategy.register(ManaKey.Type, ::createImport)
        StackExportStrategy.register(ManaKey.Type, ::createExport)
    }

    private fun createImport(level: ServerLevel, pos: BlockPos, side: Direction): StackImportStrategy =
        ManaImportStrategy(level, pos, side)

    private fun createExport(level: ServerLevel, pos: BlockPos, side: Direction): StackExportStrategy =
        ManaExportStrategy(level, pos, side)

    /** Internal units per mana for the Botania metric (granularity / manaPerAM). */
    internal val internalPerMana = ManaType.BotaniaMana.granularity.toLong()
}

private class ManaImportStrategy(
    level: ServerLevel,
    fromPos: BlockPos,
    private val side: Direction,
) : StackImportStrategy {
    private val receivers = BlockApiCache.create(BotaniaForgeCapabilities.MANA_RECEIVER, level, fromPos)

    override fun transfer(context: StackTransferContext): Boolean {
        if (!context.isKeyTypeEnabled(ManaKey.Type)) return false

        val receiver = receivers.find(side) ?: return false
        val available = receiver.currentMana.toLong()
        if (available <= 0L) return false

        val budget = context.operationsRemaining.toLong() * BotaniaManaCompat.MANA_PER_OPERATION
        val wanted = minOf(available, budget)
        if (wanted <= 0L) return false

        val key = ManaType.BotaniaMana.typeKey
        val simulated = context.internalStorage.inventory
            .insert(key, wanted * BotaniaManaCompat.internalPerMana, Actionable.SIMULATE, context.actionSource)
        // Only withdraw what the network can actually store, rounded down to whole mana.
        val acceptedInternal = simulated / BotaniaManaCompat.internalPerMana * BotaniaManaCompat.internalPerMana
        if (acceptedInternal <= 0L) return false
        val acceptedMana = acceptedInternal / BotaniaManaCompat.internalPerMana

        receiver.receiveMana(-acceptedMana.toInt())
        val inserted = context.internalStorage.inventory
            .insert(key, acceptedInternal, Actionable.MODULATE, context.actionSource)
        if (inserted < acceptedInternal) {
            // Should not happen after the simulation above; log and drop the difference.
            logger.warn("Mana import bus lost {} internal units of mana", acceptedInternal - inserted)
        }
        context.reduceOperationsRemaining(1)
        return true
    }
}

private class ManaExportStrategy(
    level: ServerLevel,
    fromPos: BlockPos,
    private val side: Direction,
) : StackExportStrategy {
    private val receivers = BlockApiCache.create(BotaniaForgeCapabilities.MANA_RECEIVER, level, fromPos)

    override fun transfer(context: StackTransferContext, what: AEKey, amount: Long): Long {
        if (what !is ManaKey || what.metric != ManaType.BotaniaMana) return 0L

        val receiver = receivers.find(side) ?: return 0L
        val simulated = context.internalStorage.inventory
            .extract(what, amount, Actionable.SIMULATE, context.actionSource)
        val wantedMana = simulated / BotaniaManaCompat.internalPerMana
        if (wantedMana <= 0L) return 0L

        val injectedMana = injectMeasured(receiver, wantedMana)
        if (injectedMana <= 0L) return 0L

        return context.internalStorage.inventory
            .extract(what, injectedMana * BotaniaManaCompat.internalPerMana, Actionable.MODULATE, context.actionSource)
    }

    override fun push(what: AEKey, amount: Long, mode: Actionable): Long {
        // Paths without a transfer context have no network to pull mana from; the export
        // bus itself always goes through [transfer]. Keep this a safe no-op.
        return 0L
    }

    /**
     * Injects up to [wantedMana] into [receiver] and returns the amount actually accepted,
     * measured via the receiver's own counter. Pools are filled precisely using their known
     * capacity; other receivers get bounded chunks with post-hoc measurement.
     */
    private fun injectMeasured(receiver: ManaReceiver, wantedMana: Long): Long {
        val pool = receiver as? ManaPool
        var remaining = wantedMana
        var totalInjected = 0L
        while (remaining > 0L) {
            var chunk = minOf(remaining, BotaniaManaCompat.MAX_INJECTION_CHUNK.toLong())
            if (pool != null) chunk = minOf(chunk, pool.maxMana.toLong() - receiver.currentMana.toLong())
            if (chunk <= 0L || receiver.isFull) break

            val before = receiver.currentMana.toLong()
            receiver.receiveMana(chunk.toInt().coerceAtMost(Int.MAX_VALUE))
            val actual = receiver.currentMana.toLong() - before
            if (actual <= 0L) break

            totalInjected += actual
            remaining -= actual
            if (actual < chunk) break // receiver clamped; stop before losing more
        }
        return totalInjected
    }
}
