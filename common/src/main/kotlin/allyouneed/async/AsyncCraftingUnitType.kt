package allyouneed.async

enum class AsyncCraftingUnitRole {
    HOST,
    CONNECTOR,
    STORAGE,
    WALL,
    GLASS,
}

enum class AsyncCraftingUnitType(
    val role: AsyncCraftingUnitRole,
    val storageBytes: Long,
    val color: Int,
    val id: String,
    val displayName: String,
) {
    HOST(AsyncCraftingUnitRole.HOST, 0, 0x3f8c52, "async_processing_host", "Async Processing Host"),
    CONNECTOR(AsyncCraftingUnitRole.CONNECTOR, 0, 0x8c6a3f, "async_processing_connector", "Async Processing Connector"),
    STORAGE(AsyncCraftingUnitRole.STORAGE, 16L * 1024 * 1024, 0x3f6e8c, "async_processing_storage", "Async Processing Storage"),
    WALL(AsyncCraftingUnitRole.WALL, 0, 0x6e6e6e, "async_processing_wall", "Async Processing Wall"),
    GLASS(AsyncCraftingUnitRole.GLASS, 0, 0x8fbf4f, "async_processing_glass", "Async Processing Glass");

    val isGridConnectedBlock: Boolean
        get() = role == AsyncCraftingUnitRole.CONNECTOR
}
