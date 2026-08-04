package allyouneed.async

/**
 * 一个方块在 async 合成网络中扮演的角色。只有需要按方块保存运行时状态的角色
 * 才拥有方块实体（BE）；其余都是纯粹的装饰/结构方块，由多方块检测器直接从
 * 世界中读取（不经过 BE）。
 *
 * Role a block plays inside the async synthesis network. Only the roles that need per-block
 * runtime state get block entities; the rest are plain structural blocks that the multiblock
 * detectors read directly from the world.
 */
enum class AsyncRole {
    /** 框架、机器方块、玻璃、强化塔：纯结构，无状态。 / Frame, machine block, glass, reinforced tower: purely structural. */
    STRUCTURAL,

    /** 能量 / 计算 / 存储 / 执行核心：目前也是纯结构。 / Energy, computing, storage and execution cores: purely structural for now. */
    CORE,

    /** 三种锚点控制器：网络控制器（处理器）、网络交换机、工厂（模块）。 / The three anchors: network controller (processor), network switch, factory (module). */
    CONTROLLER,

    /** 专用 async 线缆：连接处理器与它的交换机。 / Dedicated async cable: links a processor and its switches. */
    LINK,

    /** ME / WAN / LAN 连接器，注册为 GT 专用机器。 / ME / WAN / LAN connectors, registered as special GT machines. */
    CONNECTOR,

    /** 模块接口（Z）：async 合成模块的安装点。 / Module interface (Z): mounting point for async synthesis modules. */
    INTERFACE,
}

/**
 * async 合成系统的完整方块集合。
 *
 * The complete block set of the async synthesis system.
 *
 * 三种多方块结构共用这套方块，只是外形不同：
 * The three multiblock structures share this block set and differ only in shape:
 *   - module（工厂）：3 宽 x 7 高 x 5 深，工厂（C）位于正面
 *   - switch（交换机）：19 x 7 x 11 底座（核心 13x5x5，位于两层地板上），深度随扩展舱增长
 *   - processor（处理器）：19 x 15 x 19 底座（核心 13x13x13，位于两层地板上），深度随扩展舱增长
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
