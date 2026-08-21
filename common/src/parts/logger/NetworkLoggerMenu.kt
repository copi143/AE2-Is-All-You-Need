package allyouneed.parts.logger

import appeng.menu.AEBaseMenu
import appeng.menu.guisync.GuiSync
import appeng.menu.implementations.MenuTypeBuilder
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType

class NetworkLoggerMenu(
    id: Int,
    playerInventory: Inventory,
    private val host: NetworkLoggerBlockEntity,
) : AEBaseMenu(TYPE, id, playerInventory, host) {

    @JvmField
    @GuiSync(1)
    var conflict: Int = 0

    @JvmField
    @GuiSync(2)
    var online: Int = 0

    @JvmField
    @GuiSync(3)
    var total: Int = 0

    @JvmField
    @GuiSync(4)
    var filter: Int = NetworkLogCategory.ALL

    @JvmField
    @GuiSync(5)
    var offset: Int = 0

    @JvmField
    @GuiSync(6)
    var page: NetworkLogPage = NetworkLogPage.EMPTY

    @JvmField
    @GuiSync(7)
    var dump: NetworkLogDump = NetworkLogDump.EMPTY

    private var dumpSeq: Int = 0
    private var dumpRequested: Boolean = false

    init {
        registerClientAction(ACTION_SET_FILTER, Int::class.javaObjectType, this::setFilter)
        registerClientAction(ACTION_SET_OFFSET, Int::class.javaObjectType, this::setOffset)
        registerClientAction(ACTION_CLEAR, this::clearLogs)
        registerClientAction(ACTION_DOWNLOAD, this::requestDownload)
        refreshPage(forceLatest = true)
    }

    fun setFilter(mask: Int) {
        if (isClientSide) {
            sendClientAction(ACTION_SET_FILTER, mask)
            return
        }
        filter = mask and NetworkLogCategory.ALL
        refreshPage(forceLatest = true)
    }

    fun setOffset(value: Int) {
        if (isClientSide) {
            sendClientAction(ACTION_SET_OFFSET, value)
            return
        }
        offset = value.coerceAtLeast(0)
        refreshPage(forceLatest = false)
    }

    fun clearLogs() {
        if (isClientSide) {
            sendClientAction(ACTION_CLEAR)
            return
        }
        if (host.loggerId != 0) {
            LogStore.clear(host.loggerId)
        }
        refreshPage(forceLatest = true)
    }

    fun requestDownload() {
        if (isClientSide) {
            sendClientAction(ACTION_DOWNLOAD)
            return
        }
        dumpRequested = true
    }

    fun toggleCategory(category: NetworkLogCategory) {
        val next = if (filter and category.mask != 0) {
            filter and category.mask.inv()
        } else {
            filter or category.mask
        }
        setFilter(if (next == 0) NetworkLogCategory.ALL else next)
    }

    fun newerPage() {
        setOffset((offset + LogStore.PAGE_SIZE).coerceAtMost((total - 1).coerceAtLeast(0)))
    }

    fun olderPage() {
        setOffset((offset - LogStore.PAGE_SIZE).coerceAtLeast(0))
    }

    override fun broadcastChanges() {
        refreshPage(forceLatest = false)
        super.broadcastChanges()
    }

    private fun refreshPage(forceLatest: Boolean) {
        conflict = if (host.conflict) 1 else 0
        online = if (host.isOnline()) 1 else 0
        if (dumpRequested) {
            dumpSeq += 1
            dump = NetworkLogDump(dumpSeq, host.loggerId, LogStore.all(host.loggerId))
            dumpRequested = false
        }
        if (host.loggerId == 0) {
            total = 0
            offset = 0
            page = NetworkLogPage.EMPTY
            return
        }
        val previousTotal = total
        total = LogStore.count(host.loggerId, filter)
        val atTail = offset + LogStore.PAGE_SIZE >= previousTotal
        if (forceLatest || atTail) {
            offset = (total - LogStore.PAGE_SIZE).coerceAtLeast(0)
        } else {
            offset = offset.coerceIn(0, total)
        }
        val next = LogStore.query(host.loggerId, offset, filter, LogStore.PAGE_SIZE)
        if (page != next) {
            page = next
        }
    }

    companion object {
        val TYPE: MenuType<NetworkLoggerMenu> = MenuTypeBuilder
            .create(::NetworkLoggerMenu, NetworkLoggerBlockEntity::class.java)
            .build("network_logger")

        private const val ACTION_SET_FILTER = "setFilter"
        private const val ACTION_SET_OFFSET = "setOffset"
        private const val ACTION_CLEAR = "clear"
        private const val ACTION_DOWNLOAD = "download"
    }
}
