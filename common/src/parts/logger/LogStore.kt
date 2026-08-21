package allyouneed.parts.logger

import allyouneed.util.MODID
import allyouneed.util.logger
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger

object LogStore {
    const val MAX_LOGGER_ID = 0xFFFFFF
    const val MAX_ENTRIES = 4096
    const val PAGE_SIZE = 64
    private const val META_FILE = "meta.dat"
    private const val VERSION = 1

    @Volatile
    private var server: MinecraftServer? = null

    private val loaded = Int2ObjectOpenHashMap<NetworkLogBuffer>()
    private val nextId = AtomicInteger(1)
    private var rootDir: Path? = null

    fun attach(server: MinecraftServer) {
        this.server = server
        val dir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MODID).resolve("logs")
        Files.createDirectories(dir)
        rootDir = dir
        loadMeta(dir)
        logger.info("Network log store attached at {}", dir)
    }

    fun detach() {
        flushAll()
        NetworkLogSettle.flushIfNeeded()
        loaded.clear()
        server = null
        rootDir = null
    }

    fun isAttached(): Boolean = server != null && rootDir != null

    fun allocateId(): Int {
        if (!isAttached()) return 0
        while (true) {
            val id = nextId.getAndIncrement()
            if (id > MAX_LOGGER_ID) {
                nextId.set(MAX_LOGGER_ID + 1)
                logger.error("Network logger id space exhausted (24-bit)")
                return 0
            }
            if (!loaded.containsKey(id) && !Files.exists(logPath(id))) {
                saveMeta()
                return id
            }
        }
    }

    fun getOrLoad(loggerId: Int): NetworkLogBuffer? {
        if (loggerId !in 1..MAX_LOGGER_ID || !isAttached()) return null
        loaded[loggerId]?.let { return it }
        val data = loadLog(loggerId) ?: NetworkLogBuffer(loggerId)
        loaded[loggerId] = data
        return data
    }

    fun append(loggerId: Int, entry: NetworkLogEntry) {
        getOrLoad(loggerId)?.append(entry)
    }

    fun query(loggerId: Int, offset: Int, filter: Int, limit: Int = PAGE_SIZE): NetworkLogPage {
        val data = getOrLoad(loggerId) ?: return NetworkLogPage.EMPTY
        return data.query(offset, filter, limit)
    }

    fun count(loggerId: Int, filter: Int): Int {
        val data = getOrLoad(loggerId) ?: return 0
        if (filter == NetworkLogCategory.ALL) return data.entries.size
        return data.entries.count { it.kind.category.mask and filter != 0 }
    }

    fun all(loggerId: Int): List<NetworkLogEntry> {
        val data = getOrLoad(loggerId) ?: return emptyList()
        return ArrayList(data.entries)
    }

    fun clear(loggerId: Int) {
        getOrLoad(loggerId)?.clear()
    }

    fun delete(loggerId: Int) {
        if (loggerId !in 1..MAX_LOGGER_ID) return
        loaded.remove(loggerId)
        if (rootDir == null) return
        try {
            Files.deleteIfExists(logPath(loggerId))
        } catch (e: Exception) {
            logger.error("Failed to delete network log {}", loggerId, e)
        }
    }

    fun persist(loggerId: Int) {
        val data = loaded[loggerId] ?: return
        if (!data.dirty) return
        saveLog(data)
        data.dirty = false
    }

    fun flushAll() {
        for (data in loaded.values) {
            if (data.dirty) {
                saveLog(data)
                data.dirty = false
            }
        }
        saveMeta()
    }

    private fun logPath(loggerId: Int): Path = rootDir!!.resolve("%06x.dat".format(loggerId))

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
                    logger.warn("Unknown network log meta version {}", version)
                }
                nextId.set(input.readInt().coerceAtLeast(1))
            }
        } catch (e: Exception) {
            logger.error("Failed to load network log meta", e)
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
            logger.error("Failed to save network log meta", e)
        }
    }

    private fun loadLog(loggerId: Int): NetworkLogBuffer? {
        val path = logPath(loggerId)
        if (!Files.exists(path)) return null
        return try {
            val tag = NbtIo.readCompressed(Files.newInputStream(path))
            NetworkLogBuffer.fromNbt(loggerId, tag)
        } catch (e: Exception) {
            logger.error("Failed to load network log {}", loggerId, e)
            null
        }
    }

    private fun saveLog(data: NetworkLogBuffer) {
        val dir = rootDir ?: return
        try {
            Files.createDirectories(dir)
            val path = logPath(data.loggerId)
            val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
            NbtIo.writeCompressed(data.toNbt(), Files.newOutputStream(tmp))
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            try {
                val path = logPath(data.loggerId)
                NbtIo.writeCompressed(data.toNbt(), Files.newOutputStream(path))
            } catch (e2: Exception) {
                logger.error("Failed to save network log {}", data.loggerId, e2)
            }
        }
    }
}
