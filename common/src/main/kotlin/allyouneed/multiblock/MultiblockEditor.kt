package allyouneed.multiblock

import allyouneed.async.AsyncCraftingBlock
import allyouneed.async.AsyncCraftingUnitRole
import allyouneed.mixin.ChunkMapAccessor
import allyouneed.mixin.ServerChunkCacheAccessor
import allyouneed.util.Services
import allyouneed.util.logger
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.storage.LevelResource
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Dev-only multiblock editor dimension.
 *
 * `/ae2isallyouneed:edit_multiblock <name>` enters the editor dimension (only available in dev),
 * [`exit`] scans the currently loaded chunks of the editor level, serializes the build into the
 * pattern NBT schema and writes it to `<gameDir>/ae2isallyouneed/multiblock_override/<name>.nbt`,
 * then teleports the player back. Players that end up in the editor dimension without a session are
 * ejected on the next server tick.
 */
object MultiblockEditor {

    val EDITOR_DIMENSION: ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation("ae2isallyouneed", "editor"))

    private const val OVERRIDE_SUBDIR = "ae2isallyouneed/multiblock_override"

    private data class Session(
        val patternName: String,
        val originLevel: ResourceKey<Level>,
        val originPos: BlockPos,
    )

    private val sessions = HashMap<UUID, Session>()

    fun isEditorDimension(key: ResourceKey<Level>): Boolean = key == EDITOR_DIMENSION

    fun hasSession(player: ServerPlayer): Boolean = sessions.containsKey(player.uuid)

    fun clearSession(player: ServerPlayer) {
        sessions.remove(player.uuid)
    }

    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ae2isallyouneed:edit_multiblock")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .executes { enter(it.source, StringArgumentType.getString(it, "name")) },
                )
                .then(
                    Commands.literal("exit")
                        .executes { exit(it.source) },
                ),
        )
    }

    /** Runs every server tick; ejects players in the editor dimension without a session. */
    fun tick(server: MinecraftServer) {
        if (server.tickCount % 20 != 0) return
        val editorLevel = server.getLevel(EDITOR_DIMENSION) ?: return
        editorLevel.noSave = true
        for (player in editorLevel.players().toList()) {
            val sp = player as? ServerPlayer ?: continue
            if (hasSession(sp)) continue
            val overworld = server.overworld()
            val spawn = overworld.getSharedSpawnPos()
            sp.teleportTo(overworld, spawn.x + 0.5, spawn.y + 0.0, spawn.z + 0.5, sp.yRot, sp.xRot)
            sp.sendSystemMessage(Component.literal("编辑器维度没有进行中的会话，已传送回主世界"))
        }
    }

    fun onServerStarting(server: MinecraftServer) {
        deleteEditorDimensionFolder(server)
    }

    fun onServerStopped(server: MinecraftServer) {
        sessions.clear()
        MultiblockPatterns.overrideDir = null
        deleteEditorDimensionFolder(server)
    }

    private fun enter(source: CommandSourceStack, name: String): Int {
        if (!Services.platform.isDev()) {
            source.sendFailure(Component.literal("编辑器维度仅在开发环境可用"))
            return 0
        }
        val server = source.server
        val player = source.getPlayerOrException()
        val editorLevel = server.getLevel(EDITOR_DIMENSION) ?: run {
            source.sendFailure(Component.literal("编辑器维度不可用（数据包未包含编辑器维度）"))
            return 0
        }
        if (sessions.containsKey(player.uuid)) {
            source.sendFailure(Component.literal("你已经处于编辑器会话中，请先使用 exit 退出"))
            return 0
        }
        sessions[player.uuid] = Session(name, player.level().dimension(), player.blockPosition())
        editorLevel.noSave = true
        clearLoadedArea(editorLevel)
        val pattern = loadPattern(server, name)
        val spawn = editorSpawnPos(editorLevel, pattern)
        if (pattern != null) {
            placePattern(editorLevel, pattern, spawn)
            source.sendSuccess(
                { Component.literal("已进入编辑器维度（$name），已载入现有结构，修改后使用 exit 导出") },
                false,
            )
        } else {
            source.sendSuccess(
                { Component.literal("已进入编辑器维度（$name），未找到现有结构，请从空编辑器搭建") },
                false,
            )
        }
        val playerY = if (pattern != null) spawn.y + (pattern.height - pattern.offset.y) + 0.5 else spawn.y + 0.5
        player.teleportTo(editorLevel, spawn.x + 0.5, playerY, spawn.z + 0.5, player.yRot, player.xRot)
        return 1
    }

    private fun loadPattern(server: MinecraftServer, name: String): MultiblockPattern? {
        val dirs = listOfNotNull(MultiblockPatterns.overrideDir, File(server.getServerDirectory(), OVERRIDE_SUBDIR)).distinct()
        for (dir in dirs) {
            val file = File(dir, "$name.nbt")
            if (file.exists()) {
                try {
                    return MultiblockPatternLoader.fromNbt(NbtIo.readCompressed(file))
                } catch (e: Exception) {
                    logger.error("Failed to read override pattern {}", file, e)
                }
            }
        }
        return MultiblockPatternLoader.loadFromResource(server.getResourceManager(), name)
    }

    /**
     * Editor spawn for the build. The shared spawn is inherited from the overworld via derived level
     * data and can be anywhere (e.g. y=-60 in a void world); clamp it so the whole pattern stays
     * inside the editor's build height.
     */
    private fun editorSpawnPos(level: ServerLevel, pattern: MultiblockPattern?): BlockPos {
        val base = level.getSharedSpawnPos()
        val offsetY = pattern?.offset?.y ?: 0
        val topSpan = (pattern?.height ?: 1) - 1 - offsetY
        val minNeeded = level.minBuildHeight + offsetY
        val maxNeeded = level.maxBuildHeight - 1 - topSpan
        val y = if (minNeeded <= maxNeeded) {
            base.y.coerceIn(minNeeded, maxNeeded)
        } else {
            base.y.coerceIn(level.minBuildHeight, level.maxBuildHeight - 1)
        }
        return BlockPos(base.x, y, base.z)
    }

    /** Places the pattern in the world with the anchor (host) cell at [spawn]. */
    private fun placePattern(level: ServerLevel, pattern: MultiblockPattern, spawn: BlockPos) {
        val o = pattern.offset
        for (z in 0 until pattern.depth) {
            for (y in 0 until pattern.height) {
                for (x in 0 until pattern.width) {
                    val block = pattern.blockAt(x, y, z) ?: continue
                    val worldPos = BlockPos(spawn.x + x - o.x, spawn.y + y - o.y, spawn.z + z - o.z)
                    if (worldPos.y < level.minBuildHeight || worldPos.y >= level.maxBuildHeight) {
                        continue
                    }
                    level.getChunk(worldPos).setBlockState(worldPos, block.defaultBlockState(), false)
                }
            }
        }
    }

    private fun exit(source: CommandSourceStack): Int {
        val server = source.server
        val player = source.getPlayerOrException()
        val session = sessions.remove(player.uuid) ?: run {
            source.sendFailure(Component.literal("当前不在编辑器会话中"))
            return 0
        }
        val exported = export(session, player, server)
        if (exported != null) {
            source.sendSuccess({ Component.literal("已导出到 ${exported.absolutePath}") }, false)
        } else {
            source.sendFailure(Component.literal("导出失败：未找到已加载的异步合成核心，或构建为空"))
        }
        val target = server.getLevel(session.originLevel) ?: server.overworld()
        val pos = session.originPos
        player.teleportTo(target, pos.x + 0.5, pos.y + 0.0, pos.z + 0.5, player.yRot, player.xRot)
        source.sendSuccess({ Component.literal("已退出编辑器") }, false)
        return 1
    }

    private fun export(session: Session, player: ServerPlayer, server: MinecraftServer): File? {
        val level = player.level() as? ServerLevel ?: return null
        val pattern = scanLoadedArea(level) ?: return null
        val dir = File(server.getServerDirectory(), OVERRIDE_SUBDIR)
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("Failed to create export directory {}", dir)
            return null
        }
        val file = File(dir, "${session.patternName}.nbt")
        return try {
            NbtIo.writeCompressed(serialize(pattern), file)
            MultiblockPatterns.overrideDir = dir
            if (session.patternName == "async_crafting") {
                MultiblockPatterns.reload(server.getResourceManager())
            }
            file
        } catch (e: Exception) {
            logger.error("Failed to write exported pattern", e)
            null
        }
    }

    private fun scanLoadedArea(level: ServerLevel): MultiblockPattern? {
        val cache = level.chunkSource as? ServerChunkCache ?: return null
        val chunkMap = (cache as? ServerChunkCacheAccessor)?.getChunkMap() ?: return null

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        var nonAir = 0
        var host: BlockPos? = null
        val blockToIndex = HashMap<Block, Int>()
        val blockList = ArrayList<Block>()

        val minY0 = level.minBuildHeight
        val maxY0 = level.maxBuildHeight
        for (holder in (chunkMap as ChunkMapAccessor).invokeGetChunks()) {
            val chunk = holder.getTickingChunk() ?: continue
            val pos = chunk.pos
            for (lx in 0 until 16) {
                for (lz in 0 until 16) {
                    for (y in minY0 until maxY0) {
                        val blockPos = BlockPos(pos.minBlockX + lx, y, pos.minBlockZ + lz)
                        val block = chunk.getBlockState(blockPos).block
                        if (block == Blocks.AIR) continue
                        nonAir++
                        if (blockPos.x < minX) minX = blockPos.x
                        if (blockPos.y < minY) minY = blockPos.y
                        if (blockPos.z < minZ) minZ = blockPos.z
                        if (blockPos.x > maxX) maxX = blockPos.x
                        if (blockPos.y > maxY) maxY = blockPos.y
                        if (blockPos.z > maxZ) maxZ = blockPos.z
                        if (isHostBlock(block) && host == null) host = blockPos
                        blockToIndex.putIfAbsent(block, blockList.size.also { blockList.add(block) })
                    }
                }
            }
        }
        if (nonAir == 0) return null
        val hostPos = host ?: run {
            logger.warn("No async crafting host block found in the editor build")
            return null
        }

        val width = maxX - minX + 1
        val height = maxY - minY + 1
        val depth = maxZ - minZ + 1
        if (width <= 0 || height <= 0 || depth <= 0) return null

        val airIndex = blockToIndex.getOrPut(Blocks.AIR) { blockList.size.also { blockList.add(Blocks.AIR) } }
        val layers = Array(depth) { Array(height) { ByteArray(width) } }
        for (holder in (chunkMap as ChunkMapAccessor).invokeGetChunks()) {
            val chunk = holder.getTickingChunk() ?: continue
            val pos = chunk.pos
            for (lx in 0 until 16) {
                for (lz in 0 until 16) {
                    for (y in minY0 until maxY0) {
                        val blockPos = BlockPos(pos.minBlockX + lx, y, pos.minBlockZ + lz)
                        if (blockPos.x < minX || blockPos.x > maxX ||
                            blockPos.y < minY || blockPos.y > maxY ||
                            blockPos.z < minZ || blockPos.z > maxZ
                        ) {
                            continue
                        }
                        val block = chunk.getBlockState(blockPos).block
                        val index = if (block == Blocks.AIR) airIndex
                        else blockToIndex[block] ?: continue
                        val x = blockPos.x - minX
                        val yy = blockPos.y - minY
                        val z = blockPos.z - minZ
                        layers[z][yy][x] = index.toByte()
                    }
                }
            }
        }

        return MultiblockPattern(
            offset = BlockPos(hostPos.x - minX, hostPos.y - minY, hostPos.z - minZ),
            blocks = blockList,
            layers = layers,
        )
    }

    private fun isHostBlock(block: Block): Boolean =
        (block as? AsyncCraftingBlock)?.unitType?.role == AsyncCraftingUnitRole.HOST

    private fun serialize(pattern: MultiblockPattern): CompoundTag {
        val tag = CompoundTag()
        val o = pattern.offset
        tag.putIntArray("offset", intArrayOf(o.x, o.y, o.z))
        val blocksTag = ListTag()
        for (block in pattern.blocks) {
            val b = CompoundTag()
            b.putString("id", BuiltInRegistries.BLOCK.getKey(block).toString())
            blocksTag.add(b)
        }
        tag.put("blocks", blocksTag)
        val layersTag = ListTag()
        for (z in 0 until pattern.depth) {
            val zTag = ListTag()
            for (y in 0 until pattern.height) {
                zTag.add(ByteArrayTag(pattern.layerBytes(z, y)))
            }
            layersTag.add(zTag)
        }
        tag.put("layers", layersTag)
        return tag
    }

    private fun clearLoadedArea(level: ServerLevel) {
        val cache = level.chunkSource as? ServerChunkCache ?: return
        val chunkMap = (cache as? ServerChunkCacheAccessor)?.getChunkMap() ?: return
        val minY0 = level.minBuildHeight
        val maxY0 = level.maxBuildHeight
        for (holder in (chunkMap as ChunkMapAccessor).invokeGetChunks()) {
            val chunk = holder.getTickingChunk() ?: continue
            val pos = chunk.pos
            for (lx in 0 until 16) {
                for (lz in 0 until 16) {
                    for (y in minY0 until maxY0) {
                        val blockPos = BlockPos(pos.minBlockX + lx, y, pos.minBlockZ + lz)
                        if (chunk.getBlockState(blockPos).isAir) continue
                        chunk.setBlockState(blockPos, Blocks.AIR.defaultBlockState(), false)
                    }
                }
            }
        }
    }

    private fun deleteEditorDimensionFolder(server: MinecraftServer) {
        val folder = server.getWorldPath(LevelResource.ROOT)
            .resolve("dimensions").resolve("ae2isallyouneed").resolve("editor")
        if (!Files.exists(folder)) return
        try {
            Files.walk(folder).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete editor dimension folder {}", folder, e)
        }
    }
}
