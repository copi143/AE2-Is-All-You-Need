package allyouneed.parts.logger

enum class NetworkLogCategory(val mask: Int, val langKey: String) {
    TOPOLOGY(1, "cat.topology"),
    DEVICE(2, "cat.device"),
    ENERGY(4, "cat.energy"),
    CRAFTING(8, "cat.crafting"),
    ;

    companion object {
        const val ALL = 1 or 2 or 4 or 8
    }
}

enum class NetworkLogLevel {
    INFO,
    WARN,
    ERROR,
}

enum class NetworkLogKind(
    val category: NetworkLogCategory,
    val level: NetworkLogLevel,
    val langKey: String,
) {
    GRID_BOOT_START(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.INFO, "boot_start"),
    GRID_BOOT_END(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.INFO, "boot_end"),
    CONTROLLER_ONLINE(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.INFO, "controller_online"),
    CONTROLLER_NONE(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.WARN, "controller_none"),
    CONTROLLER_CONFLICT(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.ERROR, "controller_conflict"),
    CHANNEL_REQUIREMENT(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.INFO, "channel_req"),
    NODE_ADDED(NetworkLogCategory.DEVICE, NetworkLogLevel.INFO, "node_added"),
    NODE_REMOVED(NetworkLogCategory.DEVICE, NetworkLogLevel.INFO, "node_removed"),
    NODE_POWER_ON(NetworkLogCategory.DEVICE, NetworkLogLevel.INFO, "node_power_on"),
    NODE_POWER_OFF(NetworkLogCategory.DEVICE, NetworkLogLevel.WARN, "node_power_off"),
    NODE_CHANNEL_ON(NetworkLogCategory.DEVICE, NetworkLogLevel.INFO, "node_channel_on"),
    NODE_CHANNEL_OFF(NetworkLogCategory.DEVICE, NetworkLogLevel.WARN, "node_channel_off"),
    POWER_ON(NetworkLogCategory.ENERGY, NetworkLogLevel.INFO, "power_on"),
    POWER_OFF(NetworkLogCategory.ENERGY, NetworkLogLevel.ERROR, "power_off"),
    CRAFT_SUBMIT_OK(NetworkLogCategory.CRAFTING, NetworkLogLevel.INFO, "craft_submit_ok"),
    CRAFT_SUBMIT_FAIL(NetworkLogCategory.CRAFTING, NetworkLogLevel.WARN, "craft_submit_fail"),
    CRAFT_START(NetworkLogCategory.CRAFTING, NetworkLogLevel.INFO, "craft_start"),
    CRAFT_DONE(NetworkLogCategory.CRAFTING, NetworkLogLevel.INFO, "craft_done"),
    CRAFT_CANCEL(NetworkLogCategory.CRAFTING, NetworkLogLevel.WARN, "craft_cancel"),
    CPU_CHANGE(NetworkLogCategory.CRAFTING, NetworkLogLevel.INFO, "cpu_change"),
    LOGGER_CONFLICT(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.ERROR, "logger_conflict"),
    LOGGER_OK(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.INFO, "logger_ok"),
    UNKNOWN(NetworkLogCategory.TOPOLOGY, NetworkLogLevel.INFO, "unknown"),
    ;

    companion object {
        @JvmStatic
        fun byOrdinal(ordinal: Int): NetworkLogKind =
            entries.getOrElse(ordinal) { UNKNOWN }
    }
}
