package allyouneed.async

/**
 * Role a block plays inside the async synthesis network. Only the roles that need per-block
 * runtime state get block entities; the rest are plain decorative/structural blocks that the
 * multiblock detectors read directly from the world.
 */
enum class AsyncRole {
    /** Frame, machine block, glass, reinforced tower: purely structural. */
    STRUCTURAL,

    /** Energy / computing / storage / execution cores: purely structural for now. */
    CORE,

    /** The three anchors: network controller (processor), network switch, factory (module). */
    CONTROLLER,

    /** Dedicated async cable: links a processor and its switches. */
    LINK,

    /** ME / WAN / LAN connectors, registered as special GT hatches. */
    CONNECTOR,

    /** Module interface (Z): mounting point for async synthesis modules. */
    INTERFACE,
}

/**
 * The complete block set of the async synthesis system.
 *
 * The three multiblock structures share this block set and differ only in shape:
 *   - module: 3 wide x 7 high x 5 deep, factory (C) on the front face
 *   - switch: 19 x 7 x 11 base (13x5x5 core on a two-layer floor), depth grows with expansions
 *   - processor: 19 x 15 x 19 base (13x13x13 cube on a two-layer floor), depth grows with expansions
 */
enum class AsyncBlockKind(
    val role: AsyncRole,
    val id: String,
    val displayName: String,
    val storageBytes: Long = 0,
) {
    FRAME(AsyncRole.STRUCTURAL, "async_machine_frame", "Async Machine Frame"),
    MACHINE(AsyncRole.STRUCTURAL, "async_machine_block", "Async Machine Block"),
    GLASS(AsyncRole.STRUCTURAL, "async_machine_glass", "Async Machine Glass"),
    TOWER(AsyncRole.STRUCTURAL, "singularity_alloy_reinforced_tower", "Singularity Alloy Reinforced Tower"),
    ENERGY(AsyncRole.CORE, "async_energy_core", "Async Energy Core"),
    COMPUTING(AsyncRole.CORE, "async_computing_core", "Async Computing Core"),
    STORAGE(AsyncRole.CORE, "async_storage_core", "Async Storage Core", storageBytes = 16L * 1024 * 1024),
    EXECUTION(AsyncRole.CORE, "async_execution_core", "Async Execution Core"),
    CONTROLLER(AsyncRole.CONTROLLER, "async_network_controller", "Async Network Controller"),
    SWITCH(AsyncRole.CONTROLLER, "async_network_switch", "Async Network Switch"),
    FACTORY(AsyncRole.CONTROLLER, "async_factory", "Async Factory"),
    CABLE(AsyncRole.LINK, "async_dedicated_cable", "Async Dedicated Cable"),
    ME_CONNECTOR(AsyncRole.CONNECTOR, "async_me_connector", "Async ME Connector"),
    WAN_CONNECTOR(AsyncRole.CONNECTOR, "async_wan_connector", "Async WAN Connector"),
    LAN_CONNECTOR(AsyncRole.CONNECTOR, "async_lan_connector", "Async LAN Connector"),
    MODULE_INTERFACE(AsyncRole.INTERFACE, "async_module_interface", "Async Module Interface");
}
