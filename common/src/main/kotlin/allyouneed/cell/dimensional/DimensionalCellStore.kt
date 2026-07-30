package allyouneed.cell.dimensional

import allyouneed.util.MODID
import allyouneed.util.logger
import appeng.api.stacks.AEKey
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * World-backed storage for dimensional cells.
 * Item NBT only holds a 24-bit [cellId]; contents live under the world save directory.
 */
object DimensionalCellStore {
    const val MAX_CELL_ID = 0xFFFFFF
    private const val META_FILE = "meta.dat"
    private const val VERSION = 1

    @Volatile
    private var server: MinecraftServer? = null

    private val loaded = Int2ObjectOpenHashMap<DimensionalCellData>()
    private val nextId = AtomicInteger(1)
    private var rootDir: Path? = null

    fun attach(server: MinecraftServer) {
        this.server = server
        val dir = server.getWorldPath(LevelResource.ROOT)
            .resolve("data")
            .resolve(MODID)
            .resolve("dimensional")
        Files.createDirectories(dir)
        rootDir = dir
        loadMeta(dir)
        logger.info("Dimensional cell store attached at {}", dir)
    }

    fun detach() {
        flushAll()
        loaded.clear()
        server = null
        rootDir = null
    }

    fun isAttached(): Boolean = server != null && rootDir != null

    /**
     * Allocate a new 24-bit cell id, or 0 if exhausted / not on server.
     */
    fun allocateId(): Int {
        if (!isAttached()) return 0
        while (true) {
            val id = nextId.getAndIncrement()
            if (id > MAX_CELL_ID) {
                nextId.set(MAX_CELL_ID + 1)
                logger.error("Dimensional cell id space exhausted (24-bit)")
                return 0
            }
            if (!loaded.containsKey(id) && !Files.exists(cellPath(id))) {
                saveMeta()
                return id
            }
        }
    }

    fun getOrLoad(cellId: Int): DimensionalCellData? {
        if (cellId !in 1..MAX_CELL_ID || !isAttached()) return null
        loaded[cellId]?.let { return it }
        val data = loadCell(cellId) ?: DimensionalCellData(cellId)
        loaded[cellId] = data
        return data
    }

    fun markDirty(cellId: Int) {
        loaded[cellId]?.dirty = true
    }

    fun persist(cellId: Int) {
        val data = loaded[cellId] ?: return
        if (!data.dirty) return
        saveCell(data)
        data.dirty = false
    }

    fun flushAll() {
        for (data in loaded.values) {
            if (data.dirty) {
                saveCell(data)
                data.dirty = false
            }
        }
        saveMeta()
    }

    private fun cellPath(cellId: Int): Path =
        rootDir!!.resolve("%06x.dat".format(cellId))

    private fun metaPath(): Path = rootDir!!.resolve(META_FILE)

    private fun loadMeta(dir: Path) {
        val path = dir.resolve(META_FILE)
        if (!Files.exists(path)) {
            nextId.set(1)
            return
        }
        try {
            DataInputStream(Files.newInputStream(path)).use { input ->
                val version = input.readInt()
                if (version != VERSION) {
                    logger.warn("Unknown dimensional meta version {}", version)
                }
                nextId.set(input.readInt().coerceAtLeast(1))
            }
        } catch (e: Exception) {
            logger.error("Failed to load dimensional cell meta", e)
            nextId.set(1)
        }
    }

    private fun saveMeta() {
        val dir = rootDir ?: return
        try {
            Files.createDirectories(dir)
            DataOutputStream(Files.newOutputStream(metaPath())).use { out ->
                out.writeInt(VERSION)
                out.writeInt(nextId.get())
            }
        } catch (e: Exception) {
            logger.error("Failed to save dimensional cell meta", e)
        }
    }

    private fun loadCell(cellId: Int): DimensionalCellData? {
        val path = cellPath(cellId)
        if (!Files.exists(path)) return null
        return try {
            val tag = NbtIo.readCompressed(Files.newInputStream(path))
            DimensionalCellData.fromNbt(cellId, tag)
        } catch (e: Exception) {
            logger.error("Failed to load dimensional cell {}", cellId, e)
            null
        }
    }

    private fun saveCell(data: DimensionalCellData) {
        val dir = rootDir ?: return
        try {
            Files.createDirectories(dir)
            val tag = data.toNbt()
            val path = cellPath(data.cellId)
            val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
            NbtIo.writeCompressed(tag, Files.newOutputStream(tmp))
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            // ATOMIC_MOVE may fail on some FS; fallback
            try {
                val path = cellPath(data.cellId)
                NbtIo.writeCompressed(data.toNbt(), Files.newOutputStream(path))
            } catch (e2: Exception) {
                logger.error("Failed to save dimensional cell {}", data.cellId, e2)
            }
        }
    }
}

class DimensionalCellData(val cellId: Int) {
    val amounts = Object2ObjectOpenHashMap<AEKey, BigInteger>()
    var dirty: Boolean = false

    fun get(key: AEKey): BigInteger = amounts.getOrDefault(key, BigInteger.ZERO)

    fun set(key: AEKey, amount: BigInteger) {
        if (amount.signum() <= 0) {
            if (amounts.remove(key) != null) dirty = true
        } else {
            val prev = amounts.put(key, amount)
            if (prev == null || prev != amount) dirty = true
        }
    }

    fun add(key: AEKey, delta: BigInteger) {
        if (delta.signum() == 0) return
        val next = get(key).add(delta)
        set(key, next)
    }

    fun isEmpty(): Boolean = amounts.isEmpty()

    fun toNbt(): CompoundTag {
        val root = CompoundTag()
        root.putInt("v", 1)
        val list = ListTag()
        for ((key, amount) in amounts) {
            if (amount.signum() <= 0) continue
            val entry = CompoundTag()
            entry.put("k", key.toTagGeneric())
            entry.putByteArray("a", amount.toByteArray())
            list.add(entry)
        }
        root.put("e", list)
        return root
    }

    companion object {
        fun fromNbt(cellId: Int, tag: CompoundTag): DimensionalCellData {
            val data = DimensionalCellData(cellId)
            val list = tag.getList("e", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val entry = list.getCompound(i)
                val key = AEKey.fromTagGeneric(entry.getCompound("k")) ?: continue
                val amount = BigInteger(entry.getByteArray("a"))
                if (amount.signum() > 0) {
                    data.amounts[key] = amount
                }
            }
            data.dirty = false
            return data
        }
    }
}
