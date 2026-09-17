package allyouneed.parts.logger

import io.github.copi143.serialization.Serialize

enum class NetworkLogCategory(val mask: Int, val langKey: String) {
    Topology(1, "cat.topology"), // 拓扑
    Device(2, "cat.device"), //
    Energy(4, "cat.energy"), //
    Crafting(8, "cat.crafting"), //
    ;

    companion object {
        @JvmField
        val All = entries.fold(0) { acc, e -> acc or e.mask }
    }
}

enum class NetworkLogLevel {
    Info, Warn, Error,
}

@Serialize(ordinal = true)
enum class NetworkLogKind(val category: NetworkLogCategory, val level: NetworkLogLevel, val langKey: String) {
    GridBootStart(NetworkLogCategory.Topology, NetworkLogLevel.Info, "boot_start"), //
    GridBootEnd(NetworkLogCategory.Topology, NetworkLogLevel.Info, "boot_end"), //
    ControllerOnline(NetworkLogCategory.Topology, NetworkLogLevel.Info, "controller_online"), //
    ControllerNone(NetworkLogCategory.Topology, NetworkLogLevel.Warn, "controller_none"), //
    ControllerConflict(NetworkLogCategory.Topology, NetworkLogLevel.Error, "controller_conflict"), //
    ChannelRequirement(NetworkLogCategory.Topology, NetworkLogLevel.Info, "channel_req"), //
    NodeAdded(NetworkLogCategory.Device, NetworkLogLevel.Info, "node_added"), //
    NodeRemoved(NetworkLogCategory.Device, NetworkLogLevel.Info, "node_removed"), //
    NodePowerOn(NetworkLogCategory.Device, NetworkLogLevel.Info, "node_power_on"), //
    NodePowerOff(NetworkLogCategory.Device, NetworkLogLevel.Warn, "node_power_off"), //
    NodeChannelOn(NetworkLogCategory.Device, NetworkLogLevel.Info, "node_channel_on"), //
    NodeChannelOff(NetworkLogCategory.Device, NetworkLogLevel.Warn, "node_channel_off"), //
    PowerOn(NetworkLogCategory.Energy, NetworkLogLevel.Info, "power_on"), //
    PowerOff(NetworkLogCategory.Energy, NetworkLogLevel.Error, "power_off"), //
    CraftSubmitOk(NetworkLogCategory.Crafting, NetworkLogLevel.Info, "craft_submit_ok"), //
    CraftSubmitFail(NetworkLogCategory.Crafting, NetworkLogLevel.Warn, "craft_submit_fail"), //
    CraftStart(NetworkLogCategory.Crafting, NetworkLogLevel.Info, "craft_start"), //
    CraftDone(NetworkLogCategory.Crafting, NetworkLogLevel.Info, "craft_done"), //
    CraftCancel(NetworkLogCategory.Crafting, NetworkLogLevel.Warn, "craft_cancel"), //
    CpuChange(NetworkLogCategory.Crafting, NetworkLogLevel.Info, "cpu_change"), //
    LoggerConflict(NetworkLogCategory.Topology, NetworkLogLevel.Error, "logger_conflict"), //
    LoggerOk(NetworkLogCategory.Topology, NetworkLogLevel.Info, "logger_ok"), //
    Unknown(NetworkLogCategory.Topology, NetworkLogLevel.Info, "unknown"), //
    ;

    companion object {
        @JvmStatic
        fun byOrdinal(ordinal: Int): NetworkLogKind = entries.getOrElse(ordinal) { Unknown }
    }
}
