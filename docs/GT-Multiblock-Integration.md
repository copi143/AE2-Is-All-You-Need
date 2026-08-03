# GTCEu 多方块集成知识库（检测器驱动的三结构异步合成系统）

> 目标：把异步合成核心 + 结构 + GT 桥接**尽可能多**地放进 common source set
> （forge 专属的注册总线胶水留在 forge）。结构改为**手写常量 + 检测器驱动**
> （`AsyncStructures` 形状常量 + `AsyncStructureDetector` 世界扫描），不再有 NBT datapack，
> 也不再兼容「多方块结构编辑」维度。GTCEu 存在时控制器注册为 GT 机器
> （`MultiblockControllerMachine`），GT 缺失时走 common 自有方块/匹配器。

---

## 1. 最终决策（2026-08 更新）

| 事项 | 决策 |
| --- | --- |
| 结构真相 | **手写常量**（`AsyncStructures`），三种结构：module / switch / processor |
| 结构形态 | 统一局部坐标系：宽 × 高 × 深，控制器为锚点。扩展 = 尾部追加层，0..16 次，每次 +6 深 |
| 检测路径 | **检测器驱动**：`AsyncStructureDetector` 从控制器锚点推导朝向、探测扩展数(0..16)、逐格校验；GT 与 common 共用同一检测器 → 行为一致 |
| GT 控制器 | `MultiblockControllerMachine` 子类，**占位 pattern 仅为满足 registrate**，`checkPattern`/`asyncCheckPattern` 改走检测器 |
| GT 连接器 | GT 机器（`MetaMachine`）+ `GridNodeHolder` trait，MULTIBLOCK 节点组共享一条链路，成形后吞 32 频道 |
| 编辑器维度 | **移除**（`MultiblockEditor`、编辑器 NBT datapack 全部删除） |
| 功能 | 完全保留 AE2 异步合成：吞 32 频道 + 存储 + 对接 ME pattern |
| 菜单 | 单一 `AsyncCraftingStatusScreen` + `IAsyncCraftingStatusView`；普通菜单 + GT 菜单共用 |
| 仓口 | 不复用 GT 仓口/外壳 |
| 创造标签 | GT 机器加入 AE2 `MainCreativeTab`（`FMLCommonSetupEvent` 时用 `BlockDefinition` 包装 `def.getBlock()/getItem()`） |

---

## 2. 结构设计（common，手写）

### 2.1 形状常量：`multiblock/AsyncStructures.kt`

- `AsyncStructureType`（`baseDepth`）：
  - `MODULE(5)`：3 宽 × 7 高 × 5 深，工厂块 (1,3,0) 在前脸；模块安装于地板接口 Z 之上。
  - `SWITCH(11)`：19 宽 × 7 高 × (11+6N) 深，交换机 (9,4,3) 在核心前脸。
  - `PROCESSOR(19)`：19 宽 × 15 高 × (19+6N) 深，控制器 (9,8,3) 在核心前脸。
- 局部坐标：`x` 西→东、`y` 底→顶、`z` 前→后；控制器面朝 +z（前），结构体在控制器**后方**延伸。
- 三种格子语义：
  - **必需方块**：`blockAt(...)` 返回 kind，需经 `isValidCell(...)` 匹配；
  - **必需空气**：`blockAt(...)` 返回 null，格子必须为空（如处理器 7×7 空气层）；
  - **任意格**：`isDonCare(...)` 为 true，接受任何方块。
- `isValidCell` 替换规则：墙上的机器方块可用玻璃替换（地板层除外）；核心外壳的机器方块可按
  结构类型被对应连接器替换（SWITCH：WAN/LAN；PROCESSOR：ME/LAN）。
- 扩展层（switch/processor）：从 `baseDepth` 起每 +6 深追加一个 bay，bay 中心行放两个模块
  接口（Z）。`MAX_EXTENSIONS = 16`，超 16 自动拒绝。
- 世界偏移（`worldOffset`）：相对锚点，`right = facing.getClockWise()`，`+z` 沿 facing。

### 2.2 检测器：`async/AsyncStructureDetector.kt`

- `facingOf`：优先读 `BlockStateProperties.HORIZONTAL_FACING`；GT 机器扩展旋转态回退读
  `GTBlockStateProperties.UPWARDS_FACING`。
- `detectModule(level, interfacePos)`：从地板接口 Z 向上探测其上的模块（3×7×5 全必需），
  工厂块 = 接口 + `(2*facing, -4, 2*facing)`，且朝向一致。
- `detectSwitch(level, controllerPos)`：对扩展数 0..16 逐一 `scanStructure`，首次成功即返回；
  WAN 上限 1、LAN 上限 2；再对每个接口 `detectModule`。
- `detectProcessor(...)`：同理；ME 上限 1、LAN 上限 2；随后 `linkSwitches` 从处理器 LAN 经
  专用线缆（CABLE）级联到交换机 WAN，再递归每台交换机的 LAN。
- `findHostController`：以方块为中心的有界搜索，用于把上游 rescan 通知路由到成形控制器。

---

## 3. 菜单/屏幕抽象（common）

- `IAsyncCraftingStatusView`（common）：`formed/gridConnected/swallowedChannels/storageBytes/
  blockCount/infiniteChannelMode` 只读字段。
- `AsyncCraftingStatusMenu`（普通）：host = `AsyncStructureBlockEntity`，读
  `getProcessorCluster()/getSwitchCluster()/getModuleCluster()/getConnectorViews()`；
  TYPE = `"async_crafting_status"`。
- `AsyncStructureGtStatusMenu`（GT）：host = `BlockEntity`（经 AE2 `MenuTypeBuilder`，不引用
  `MetaMachineBlockEntity`，forge 专属类不出现在签名里）；读 `MetaMachine.getMachine(...)`
  转 `AsyncStructureGtControllerMachine` 的 `getCluster()/getConnectorViews()`；
  TYPE = `"async_crafting_status_gt"`。
- `AsyncCraftingStatusScreen` 泛型化 `<M : AbstractContainerMenu & IAsyncCraftingStatusView>`，
  两个菜单共用；forge 侧两个 TYPE 都注册到该屏幕，fabric 侧只注册普通菜单。

---

## 4. GT 控制器：`gt/AsyncStructureGtControllerMachine.kt`

- 抽象基类 `AsyncStructureGtControllerMachine : MultiblockControllerMachine, IInteractedMachine`；
  三个子类：`AsyncStructureGtProcessorMachine` / `AsyncStructureGtSwitchMachine` /
  `AsyncStructureGtFactoryMachine`。
- 工厂控制器没有结构锚（模块由接口探测），`detect()` 用
  `getPos().offset(2*facing.stepX, -4, 2*facing.stepZ)` 反推接口位置再 `detectModule`。
- `checkPattern()`：跑 `AsyncStructureDetector`，成功则把结构 bounds 角点 + 连接器位置写入
  `MultiblockState` pos cache（保证方块变化能触发重检）。
- `asyncCheckPattern()`：**不在异步线程碰世界** —— 检测延迟到主线程，在 `patternLock` 内执行
  `checkPatternWithLock() + onStructureFormed()`，并把状态注册进 `MultiblockWorldSavedData`。
- `onStructureFormed()`：重建 cluster，逐个连接器 `setHostController(this)`；
  `onStructureInvalid()`：逐个 `setHostController(null)` 并销毁 cluster。
- 占位 pattern（`gt/AsyncStructureGtPattern.kt`）：单 aisle `"C"` + 控制器谓词，**只为满足
  registrate**，实际成形判定完全由检测器决定；`allowFlip(false)`。

---

## 5. GT 连接器：`gt/AsyncStructureGtConnectorMachine.kt`

- 抽象基类 `AsyncStructureGtConnectorMachine : MetaMachine, IGridConnectedMachine,
  IAsyncChannelSink, IAsyncChannelView`；三个具体子类（ME / WAN / LAN）—— 抽象基类不能
  直接实例化，GTRegistrate 的元编程需要具体类的构造器引用。
- `AsyncStructureGridNodeTrait : GridNodeHolder` 覆写 `createManagedNode()`：
  - `setFlags(MULTIBLOCK, REQUIRE_CHANNEL, DENSE_CAPACITY)` + `setExposedOnSides(emptySet())`，
    cast 回 `SerializableManagedGridNode`（基类方法返回 ManagedGridNode）；
  - `addService(IGridMultiblock, ...)`：收集同主机控制器的全部连接器节点 → 一个链路通道组。
- 成形门控：`isFormed()` = `hostController?.isFormed() == true`；暴露面由
  `updateExposedSides()`（成形=前脸，未成形=空）驱动，`setExposedOnSides` 变化触发 pathing
  重算（无需 mixin 改动）。
- 频道吞没：`swallowedChannels` 读 `(node as? AsyncChannelNodeHolder)?.getAsyncSwallowedChannels()`
  （`PathingCalculationMixin`/`GridNodeMixin` 注入不变）；DENSE → 32 频道。
- 普通（非 GT）连接器 `AsyncStructureConnectorBlockEntity`（common）行为等价：`AENetworkBlockEntity`
  + 同一组 flags + `IGridMultiblock` 收集同结构节点。

---

## 6. 注册胶水（forge）

### 6.1 `GTAsyncCrafting`（forge/.../init/GTAsyncCrafting.kt）

- **不能用 `GTRegistrate.registerEventListeners(bus)`**：其内部经
  `OneTimeEventReceiver` → `AbstractRegistrate.getModEventBus()` → `FMLJavaModLoadingContext.get()`，
  在 KFF 下抛 ClassCastException。改为匿名子类 `object : GTRegistrate(MODID)`：
  - **覆写 `getModEventBus()` 返回 KFF `MOD_BUS`**（内部注册步骤同样会触发）；
  - 暴露受保护处理器，直接在 `MOD_BUS` 上注册 `onRegister`(LOW) / `onRegisterLate`(LOWEST) /
    `onBuildCreativeModeTabContents`。
- **机器注册不能放在 mod 构造期**：GTCEu 在 `FMLConstructModEvent.enqueueWork` 里注册材料/
  机器/配方类型并逐个冻结 `GTRegistry`。正确窗口 = GTCEu 的
  `GTCEuAPI.RegisterEvent`（mod-bus 事件，`GenericEvent<MachineDefinition>`，用
  `bus.addGenericListener(MachineDefinition::class.java, Consumer<RegisterEvent<RL, MachineDefinition>>)`），
  GTCEu 注册完自有机器、冻结 `gtceu:machine` 之前广播。
- 控制器：`registrate.multiblock(id, machineFactory, blockFactory, itemFactory, blockEntityFactory)
  .pattern { placeholderAsyncStructurePattern(it) }.allowFlip(false).register()`。
- 连接器：`registrate.machine(id, Function { MachineDefinition(it) }, machineFactory, ...)`。
- 自定义工厂：`blockFactory<D>` → `AsyncStructureGtMachineBlock(props, definition, kind)`（实现
  `IAsyncKindBlock`，显式覆写 `newBlockEntity` 兼容 fabric 编译的 vanilla `EntityBlock`）；
  `itemFactory` → `MetaMachineItem`；`blockEntityFactory` → `MetaMachineBlockEntity`
  （用 `org.apache.commons.lang3.function.TriFunction` + 显式泛型 `BiFunction` 满足 SAM）。
- `AsyncBlockRegistry.register(kind, definition.getBlock())` 按 kind 登记；AE2 创造标签在
  `FMLCommonSetupEvent` 再处理（此时 RegistryObject 才被填充）。
- `definition(kind)` / `isGtOwned(kind)` 访问器。

### 6.2 `ForgeBlocks` / `FabricBlocks`

- `ForgeBlocks`：`hasGt = isModLoaded("gtceu")`；`gtOwnedKinds` = CONTROLLER + CONNECTOR 角色全部
  kind；`asyncStructureKinds` 排除它们，其余 10 个 kind 的方块/item/BE/创造标签照常注册
  （`async_structure` BE、`async_structure_connector` BE）。GT 已删除旧 tower 单元
  （host/connector/storage/wall/glass）。
- `FabricBlocks`：恒无 GT，16 个 kind 全部注册；登记进 `AsyncBlockRegistry`。
- 玻璃渲染：普通与 GT 情形统一 `AsyncBlockRegistry.get(AsyncBlockKind.GLASS)` 挂 cutout。

---

## 7. 验证清单

- **Forge 带 GT**：搭处理器（核心 + 扩展 bay + 模块）→ 成形；拆装扩展层（0..16）→ 重成形；
  右键 GT 控制器开 GT 菜单；接 ME 吞 32 频道；拆连接器 → 失形。
- **Forge 无 GT**：common 匹配器跑同一结构；`./gradlew :forge:compileKotlin` + 运行验证。
- **Fabric**：恒无 GT，common 匹配器回归。
- 三模块 `compileKotlin` / `build` 全绿。

---

## 8. 已知取舍与边界

- common 引用 GT 类 → fabric 编译 classpath 需 `compileOnly` GTCEu（含其内嵌 LDLib/Registrate
  的摊平解压）+ forge universal（`IForgeBlockEntity`）；运行期 fabric 绝不加载。
- `IMachineBlockEntity extends IForgeBlockEntity`（forge）—— common 只经构造参数间接用到，
  fabric 编译仍须 forge jar 在 classpath。
- GT 占位 pattern 不表达真实形状：成形信息完全来自检测器，`MultiblockState` pos cache 只是让
  GT 的重检触发机制（方块更新）能覆盖到结构 bounds。
- `checkPattern()` 在调用线程（可能是异步）同步跑 `detect()`（纯世界读）；`asyncCheckPattern()`
  把主线程任务放进 `patternLock` —— 若出现数据竞争再收紧。
- 扩展 0..16，每次 +6 深；超 16 自动拒绝；不检查结构外多余方块（与旧行为一致）。
- 级联：处理器 → LAN → 专用线缆 → 交换机 WAN；交换机 LAN 可再级联（`linkSwitches` 递归）。

---

## 9. 源码位置备忘

- GTCEu 7.5.3 源码：`/tmp/opencode/gtceu/src`
  - `api/pattern/{FactoryBlockPattern,BlockPattern,MultiblockState,MultiblockWorldSavedData}.java`
  - `api/machine/multiblock/MultiblockControllerMachine.java`（`checkPattern`/`asyncCheckPattern`/
    `onStructureFormed`/`patternLock`/`getMultiblockState`）
  - `api/machine/{MetaMachine,IMachineBlockEntity,MachineDefinition,MultiblockMachineDefinition}.java`
  - `api/block/MetaMachineBlock.java`（`use` → `IInteractedMachine.onUse`）
  - `integration/ae2/machine/trait/GridNodeHolder.java`（`createManagedNode`、`mainNode`）
  - `integration/ae2/utils/SerializableManagedGridNode.java`
- AE2 15.4.10 源码：`/tmp/opencode/ae2src`
  - `appeng/menu/implementations/MenuTypeBuilder.java`
  - `appeng/core/MainCreativeTab.java`
- 本项目：
  - `common/.../multiblock/AsyncStructures.kt`（形状常量）
  - `common/.../async/{AsyncStructureDetector,AsyncStructureCalculator,AsyncStructureCluster,
    AsyncStructureBlockEntity(含 AsyncStructureConnectorBlockEntity),AsyncStructureBlock,
    AsyncBlockKind,AsyncBlockRegistry,AsyncCraftingRegistration,AsyncCraftingStatusMenu,
    AsyncCraftingStatusScreen,IAsyncCraftingStatusView,IAsyncChannelView,IAsyncChannelSink,
    IAsyncKindBlock}`
  - `common/.../gt/{AsyncStructureGtControllerMachine,AsyncStructureGtConnectorMachine,
    AsyncStructureGtMachineBlock,AsyncStructureGtPattern,AsyncStructureGtStatusMenu}.kt`
  - `forge/.../{ExampleMod,init/ForgeBlocks,init/GTAsyncCrafting,forge/client/ForgeClientEvents}`
  - `fabric/.../init/FabricBlocks`
