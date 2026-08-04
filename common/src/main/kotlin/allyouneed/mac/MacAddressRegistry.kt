package allyouneed.mac

import allyouneed.util.MODID
import allyouneed.util.logger
import appeng.api.networking.IGridNode
import appeng.me.ManagedGridNode
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * World-backed 48-bit MAC allocator and live-node lookup.
 *
 * Allocation counter is persisted under `world/data/<modid>/mac/meta.dat`.
 * Live lookup uses weak references so chunk unload does not leak nodes.
 *
 * Collision rule: a MAC may only map to one live node. If NBT/wrench restores a MAC
 * already held by a different live node, a new MAC is allocated.
 */
object MacAddressRegistry {
    private const val META_FILE = "meta.dat"
    private const val VERSION = 1

    /** Sequential counter lives in the lower 46 bits; LAA bit is or'd on allocate. */
    private const val COUNTER_MASK = 0x3FFF_FFFF_FFFFL

    @Volatile
    private var server: MinecraftServer? = null
    private var rootDir: Path? = null
    private val nextCounter = AtomicLong(1)
    private val live = Long2ObjectOpenHashMap<WeakReference<IGridNode>>()

    fun attach(server: MinecraftServer) {
        this.server = server
        val dir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MODID).resolve("mac")
        Files.createDirectories(dir)
        rootDir = dir
        loadMeta(dir)
        synchronized(live) { live.clear() }
        logger.info("MAC address registry attached at {}", dir)
    }

    fun detach() {
        saveMeta()
        synchronized(live) { live.clear() }
        server = null
        rootDir = null
    }

    fun isAttached(): Boolean = server != null && rootDir != null

    /**
     * Allocate a new 48-bit MAC that is not currently registered to a live node,
     * or [MacAddress.NONE] if not on server / exhausted.
     */
    @JvmStatic
    fun allocate(): Long {
        if (!isAttached()) return MacAddress.NONE
        while (true) {
            val counter = nextCounter.getAndIncrement()
            if (counter > COUNTER_MASK) {
                nextCounter.set(COUNTER_MASK + 1)
                logger.error("MAC address space exhausted (46-bit counter)")
                return MacAddress.NONE
            }
            val mac = MacAddress.normalize(counter or MacAddress.LOCALLY_ADMINISTERED)
            if (mac == MacAddress.NONE) continue
            if (isLive(mac)) continue
            saveMeta()
            return mac
        }
    }

    /**
     * @return true if [mac] is bound to [node] (or was free and is now bound)
     */
    @JvmStatic
    fun register(mac: Long, node: IGridNode): Boolean {
        if (!MacAddress.isValid(mac)) return false
        val key = MacAddress.normalize(mac)
        synchronized(live) {
            val ref = live.get(key)
            val existing = ref?.get()
            if (existing != null && existing !== node) {
                return false
            }
            if (existing === node) {
                return true
            }
            // stale weak ref or free slot
            live.put(key, WeakReference(node))
            return true
        }
    }

    @JvmStatic
    fun unregister(mac: Long, node: IGridNode) {
        if (!MacAddress.isValid(mac)) return
        val key = MacAddress.normalize(mac)
        synchronized(live) {
            val ref = live.get(key) ?: return
            if (ref.get() === node || ref.get() == null) {
                live.remove(key)
            }
        }
    }

    @JvmStatic
    fun lookup(mac: Long): IGridNode? {
        if (!MacAddress.isValid(mac)) return null
        val key = MacAddress.normalize(mac)
        synchronized(live) {
            val ref = live.get(key) ?: return null
            val node = ref.get()
            if (node == null) {
                live.remove(key)
                return null
            }
            return node
        }
    }

    /** True if some other live node currently holds [mac]. */
    @JvmStatic
    fun isLiveConflict(mac: Long, self: IGridNode): Boolean {
        val other = lookup(mac) ?: return false
        return other !== self
    }

    @JvmStatic
    fun isLive(mac: Long): Boolean = lookup(mac) != null

    /**
     * Ensure [managed]/node] has a unique MAC (skip/clear cables), register lookup.
     */
    @JvmStatic
    fun ensureAndBind(managed: IManagedMacAddressHolder, node: IGridNode) {
        if (!MacPolicy.shouldHaveMac(node)) {
            clearBound(managed, node)
            return
        }

        var mac = managed.macAddress
        if (!MacAddress.isValid(mac) && node is IMacAddressHolder) {
            mac = node.macAddress
        }

        if (MacAddress.isValid(mac) && isLiveConflict(mac, node)) {
            logger.warn(
                "MAC {} already in use by another node; reallocating for {}",
                MacAddress.format(mac),
                node.owner?.javaClass?.simpleName ?: node,
            )
            mac = MacAddress.NONE
        }

        if (!MacAddress.isValid(mac)) {
            mac = allocate()
        }

        if (!MacAddress.isValid(mac)) {
            clearBound(managed, node)
            return
        }

        // Claim slot; if race lost, allocate again.
        var attempts = 0
        while (!register(mac, node)) {
            attempts++
            if (attempts > 8) {
                logger.error("Failed to claim unique MAC after retries for {}", node.owner)
                clearBound(managed, node)
                return
            }
            mac = allocate()
            if (!MacAddress.isValid(mac)) {
                clearBound(managed, node)
                return
            }
        }

        managed.macAddress = mac
        if (node is IMacAddressHolder) {
            node.macAddress = mac
        }
    }

    @JvmStatic
    fun collectFromManaged(nodes: Iterable<ManagedGridNode>): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        for (managed in nodes) {
            if (managed !is IManagedMacAddressHolder) continue
            if (MacPolicy.isCableManaged(managed)) continue
            val mac = managed.macAddress
            if (MacAddress.isValid(mac)) {
                result[managed.macTagName] = MacAddress.normalize(mac)
            }
        }
        return result
    }

    @JvmStatic
    fun applyToManaged(managed: ManagedGridNode, macs: Map<String, Long>) {
        if (managed !is IManagedMacAddressHolder) return
        if (MacPolicy.isCableManaged(managed)) {
            clearBound(managed, managed.node)
            return
        }
        val mac = macs[managed.macTagName] ?: return
        if (!MacAddress.isValid(mac)) return

        val node = managed.node
        if (node != null && isLiveConflict(mac, node)) {
            logger.warn(
                "MAC {} from item conflicts with live node; keeping allocation path for {}",
                MacAddress.format(mac),
                node.owner?.javaClass?.simpleName ?: node,
            )
            // Leave current / let ensureAndBind allocate fresh — do not force conflict.
            return
        }

        managed.macAddress = mac
        if (node is IMacAddressHolder) {
            node.macAddress = mac
            if (!register(mac, node)) {
                // Lost race: clear and let next ensureAndBind fix
                managed.macAddress = MacAddress.NONE
                node.macAddress = MacAddress.NONE
            }
        }
    }

    private fun clearBound(managed: IManagedMacAddressHolder, node: IGridNode?) {
        val old = managed.macAddress
        if (MacAddress.isValid(old) && node != null) {
            unregister(old, node)
        }
        if (node is IMacAddressHolder) {
            val nodeMac = node.macAddress
            if (MacAddress.isValid(nodeMac)) {
                unregister(nodeMac, node)
                node.macAddress = MacAddress.NONE
            }
        }
        managed.macAddress = MacAddress.NONE
    }

    private fun loadMeta(dir: Path) {
        val path = dir.resolve(META_FILE)
        if (!Files.exists(path)) {
            nextCounter.set(1)
            return
        }
        try {
            DataInputStream(Files.newInputStream(path)).use { input ->
                val version = input.readInt()
                if (version != VERSION) {
                    logger.warn("Unknown MAC meta version {}", version)
                }
                nextCounter.set(input.readLong().coerceAtLeast(1L))
            }
        } catch (e: Exception) {
            logger.error("Failed to load MAC meta", e)
            nextCounter.set(1)
        }
    }

    private fun saveMeta() {
        val dir = rootDir ?: return
        try {
            Files.createDirectories(dir)
            DataOutputStream(Files.newOutputStream(dir.resolve(META_FILE))).use { out ->
                out.writeInt(VERSION)
                out.writeLong(nextCounter.get())
            }
        } catch (e: Exception) {
            logger.error("Failed to save MAC meta", e)
        }
    }
}
