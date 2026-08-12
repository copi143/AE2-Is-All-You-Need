# GTCEu 多方块集成知识库（异步合成系统：检测器驱动 → 分组重复 mixin 原生 pattern）

> 目标：把异步合成核心 + 结构 + GT 桥接**尽可能多**地放进 common source set
> （forge 专属的注册总线胶水留在 forge）。结构为**手写常量**（`AsyncStructures` 单一真相源），
> 不再有 NBT datapack，也不再兼容「多方块结构编辑」维度。GTCEu 存在时控制器注册为 GT 机器
> （`MultiblockControllerMachine`），GT 缺失时走 common 自有方块/匹配器。
>
> **架构两阶段（2026-08）**：①旧——检测器驱动（`AsyncStructureDetector` 逐格判形，已实现已提交）；
> ②新——switch/processor 的 GT 控制器改用 **native GTCEu `BlockPattern` 判形**，配一个「分组重复
> （6 层一组）」mixin；检测器仅保留给 vanilla 路径与模块探测。修改大纲见 §1。

---

## 1. 架构演进与修改大纲（2026-08：检测器驱动 → 分组重复 mixin）

> 本节是**目标态**的修改大纲，供后续（压缩上下文后）直接照此实现；旧架构细节仍保留在各节，
> 已标注「历史」。核心动机：switch/processor 尾部是「每 6 层一组、重复 0..16 次」的扩展 bay，
> GTCEu 只支持单 aisle 重复，因此给 `BlockPattern` 加「组重复」语义的 mixin，让 GT 控制器改用
> **原生 pattern 判形**，去掉检测器路径的自定义匹配/失效/轮询代码。

### 1.1 最终决策（旧架构，检测器驱动，已实现）

| 事项 | 决策 |
| --- | --- |
| 结构真相 | **手写常量**（`AsyncStructures`），三种结构：module / switch / processor |
| 结构形态 | 统一局部坐标系：宽 × 高 × 深，控制器为锚点。扩展 = 尾部追加层，0..16 次，每次 +6 深 |
| 检测路径 | **检测器驱动**：`AsyncStructureDetector` 从控制器锚点推导朝向、探测扩展数(0..16)、逐格校验；GT 与 common 共用同一检测器 → 行为一致 |
| GT 控制器 | `MultiblockControllerMachine` 子类，**pattern 由形状常量生成（供潜行预览/JEI 显示）**，`checkPattern`/`asyncCheckPattern` 改走检测器 |
| GT 连接器 | GT 机器（`MetaMachine`）+ `GridNodeHolder` trait，MULTIBLOCK 节点组共享一条链路，成形后吞 32 频道 |
| 编辑器维度 | **移除**（`MultiblockEditor`、编辑器 NBT datapack 全部删除） |
| 功能 | 完全保留 AE2 异步合成：吞 32 频道 + 存储 + 对接 ME pattern |
| 菜单 | 单一 `AsyncCraftingStatusScreen` + `IAsyncCraftingStatusView`；普通菜单 + GT 菜单共用 |
| 仓口 | 不复用 GT 仓口/外壳 |
| 创造标签 | GT 机器加入 AE2 `MainCreativeTab`（`FMLCommonSetupEvent` 时用 `BlockDefinition` 包装 `def.getBlock()/getItem()`） |

### 1.2 目标态决策（新架构，待实现）

| 事项 | 决策 |
| --- | --- |
| switch/processor GT 成形 | **native `BlockPattern` 判形**（mixin 支持 6 层组重复），删除检测器路径的 `checkPattern`/`asyncCheckPattern`/`requestStructureCheck` |
| 形状真相 | 仍是 `AsyncStructures`（单一真相源）；GT pattern 由它生成 |
| 检测器新用途 | 仅剩：vanilla（非 GT）结构判形 + 模块探测（工厂机 `detectModule`）；普通路径全部不变 |
| 组重复编码 | 组首 aisle `aisleRepetitions[c] = [min,max]`（组整体重复次数），组内其余 G-1 个 aisle 保持 `[1,1]`；新增 `groupSizes` 表标记组首 |
| 匹配重复数 | min=0（与检测器 0..16 完全一致，N=0 允许成形） |
| 预览 | 第 0 页 = 1 次重复（in-world 叠影取 `getMatchingShapes().get(0)`）；JEI 页序 1..16,0 |
| autoBuild | 组默认放 `max(1, min)` = 1 组（6 层） |
| 连接器/菜单/渲染/注册胶水 | 全部不变 |
| 连接器计数上限 | 由谓词 `setMaxGlobalLimited` 表达，与检测器一致：wan≤1 / lan≤2（switch），me≤1 / lan≤2（processor） |

### 1.3 修改大纲（文件级清单）

| 文件 | 改动 |
| --- | --- |
| `common/src/main/java/allyouneed/mixin/gtceu/BlockPatternGroupedMixin.java` | 新增。`@Unique int[] groupSizes`；`@Overwrite` `checkPatternAt(6参)` / `getPreview` / `autoBuild` |
| `common/src/main/kotlin/allyouneed/gt/IGroupedBlockPattern.kt` | 新增接口：`setGroup(aisleIndex, groupSize)` / `getGroupSize(aisleIndex)`（mixin `@Unique` 字段实现） |
| `common/src/main/java/allyouneed/mixin/gtceu/MultiblockMachineDefinitionGroupMixin.java` | 新增。`@Overwrite getMatchingShapes()`：组首且 min==0 的维度页面序 = 1..max 再补 0 |
| `common/src/main/resources/ae2isallyouneed.mixins.json` | 注册上述两个 mixin（`allyouneed.mixin.gtceu` 包，Java 文件）；`plugin` 为 `allyouneed.mixin.MyMixinPlugin`（通用可选依赖守卫） |
| `common/src/main/kotlin/allyouneed/gt/AsyncStructureGtPattern.kt` | 重写 `build()`：发全深度布局（base + 组 + 收尾 + 后墙），构造后 `setGroup(baseCount, 6)` |
| `common/src/main/kotlin/allyouneed/multiblock/AsyncStructures.kt` | `isFloorCell` / `inCore` / `isOuterShellCell` 改为 public（pattern 生成器需要） |
| `common/src/main/kotlin/allyouneed/gt/AsyncStructureGtControllerMachine.kt` | 删检测器覆写；`rebuildCluster` 改从 pattern 的 pos cache 汇总 cluster 摘要 |
| `common/src/main/kotlin/allyouneed/multiblock/async/AsyncStructureNotifier.kt` | 删 GT 分支（`requestStructureCheck()`），保留 vanilla 分支 |

### 1.4 mixin 设计细节（核心，GTCEu 7.5.3）

关键事实：

- `IMultiController.checkPattern()` → `getPattern().checkPatternAt(state, false)` → 2 参 → 6 参
  `checkPatternAt(state, centerPos, frontFacing, upwardsFacing, isFlipped, savePredicate)`。
- 异步流程：`MultiblockWorldSavedData.searchingTask`（后台线程）→ `asyncCheckPattern(periodID)` →
  `checkPatternWithTryLock()` 在**异步线程**跑匹配；成形后 `server.execute { checkPatternWithLock();
  onStructureFormed(); addMapping; removeAsyncLogic }` 在**主线程**再跑一次匹配再 `onStructureFormed()`。
  → mixin 匹配代码必须异步安全：只经 `MultiblockState.update` 读世界（LevelMixin 保证 off-thread 安全）；
  `onStructureFormed` 必然主线程，`rebuildCluster` 可安全读世界。
- `MultiblockMachineDefinition.getMatchingShapes()`（懒加载 memoized）用 `repetitionDFS` 枚举
  `aisleRepetitions` 每维 [min,max] → 组编码下组=1 个维度 → 17 页（不会 16^6 爆炸）。
  `MultiblockInWorldPreviewRenderer.showPreview`（in-world 叠影）与 `PatternPreviewWidget`（JEI）都消费
  `getMatchingShapes()`。
- `formedRepetitionCount[]` 只有赋值、无下游读取（死字段），组编码不影响其他逻辑。
- 计数谓词：`SimplePredicate.testGlobal` 逐格累计，超 `maxCount` 报 `SinglePredicateError`；
  `blocks(a).or(blocks(b).setMaxGlobalLimited(n))` 把 b 移到 limited 并限次，a 留在 common 不限量。

`BlockPatternGroupedMixin`：

- `groupSizes[c]`：组首 = G；组内其余 = 0；单 aisle = 1。
- `checkPatternAt(6参)` 重构循环：外层按「步」迭代（单 aisle 或组）。组步依次匹配
  `blockMatches[c..c+G-1]` 共 G 个连续 z（每组内 z 逐个递增），一组完整成功后 `z += G`；组重复计数
  落在 `[aisleRepetitions[组首][0], [组首][1]]`。**逐格逻辑原样保留**：findFirstAisle 滑动/回退
  （`r < min` 时 `r=c=0; z=minZ++; matchContext.reset(); findFirstAisle=false`）、`layerCount` 层限制、
  `ioMap`、parts 共享（`canPartShared` / share 错误）、`vaBlocks`(ActiveBlock)、pos cache（`addCache`）、
  `savePredicate` 的 predicates 记录、各错误路径（`PatternStringError`/`SinglePredicateError`/`PatternError`）；
  组内 G 个 aisle 的 `formedRepetitionCount` 都写组计数。
- `getPreview(int[] repetition)`：组首按 `repetition[组首]` 次渲染 `blockMatches[组首..组首+G-1]`
  G 个 slice（组内依次渲染、x 递增），组内其余 aisle 跳过。
- `autoBuild`：组按 `max(1, aisleRepetitions[组首][0])` 次放置（匹配仍允许 0，构建默认 1 组）；
  层/全局 limited 计数、candidates 选块、放置与朝向修正逻辑不变。

`MultiblockMachineDefinitionGroupMixin`：

- `@Overwrite getMatchingShapes()`：`pattern is IGroupedBlockPattern` 且某组首 min==0 → 该维度枚举序
  `1..max, 0`（保证第 0 页 = 1 次重复）；其余维度维持 `min..max`。

### 1.5 新 pattern 布局与谓词（`AsyncStructureGtPattern.build` 重写）

布局（全部与扩展数 N 无关；N=0 → base+收尾+后墙，N → 总深 +6N）：

| 结构 | base aisles | 组（6 层，重复 [0,16]） | 收尾 | 后墙 | 总 aisle 数 |
| --- | --- | --- | --- | --- | --- |
| SWITCH | z=0..8（9） | z=9..14 | z=15 | z=16 | 17 |
| PROCESSOR | z=0..16（17） | z=17..22 | z=23 | z=24 | 25 |

组内容（每层 19×height；y=0 地板：`F@x=0,18`、`M@x=1..17`；y=1 各行见 `upperFloorCell`；y≥2 全
don't-care）：

- row0/row4：`F@x=1,9,17`，其余 M（x=0,18 为**必需空气**）；
- row1/row3：`F@x=1,9,17`、`M@x=2,8,10,16`，其余空气；
- row2（接口行）：`Z@x=5,13`、`F@x=1,9,17`、`M@x=2,8,10,16`，其余空气；
- row5：`F@x=1,17`、`TOWER@x=2..16`，x=0,18 空气。

谓词（对应 `AsyncStructures.isValidCell` 替换规则）：

- 地板层（y∈{0,1}）MACHINE → `blocks(machine)`；
- 非地板 MACHINE → `blocks(machine).or(blocks(glass))`；
- SWITCH 核心内（`inCore`）M 格 →
  `blocks(machine).or(blocks(glass)).or(blocks(wan).setMaxGlobalLimited(1)).or(blocks(lan).setMaxGlobalLimited(2))`；
- PROCESSOR 外壳单面（`isOuterShellCell`）M 格 → 同上，`blocks(me).setMaxGlobalLimited(1)` / `blocks(lan).setMaxGlobalLimited(2)`；
- 控制器格 → `Predicates.controller(Predicates.blocks(definition.getBlock()))`；
- 必需空气 → `Predicates.air()`；任意格 → 默认 any；FRAME/TOWER/ENERGY/COMPUTING/STORAGE/EXECUTION/MODULE_INTERFACE → 各自块。
- base 层核心函数格（ENERGY/TOWER/COMPUTING/STORAGE 等）照 `blockAt` 原样。
- 构造完成后：`(pattern as IGroupedBlockPattern).setGroup(baseCount, 6)`（baseCount = 9/17）。

前置：`AsyncStructures.isFloorCell` / `inCore` / `isOuterShellCell` 需 public。

### 1.6 GT 控制器简化（`gt/AsyncStructureGtControllerMachine.kt`）

- **删**：`checkPattern()` / `asyncCheckPattern()` / `requestStructureCheck()` 覆写、`cachePositions`、
  `detection` 字段、`AsyncStructureNotifier` 的 GT 分支调用。
- **留**：`onStructureFormed` / `onStructureInvalid`（super + `updateFormedBlockState` + 重建/销毁
  cluster）、`onMachineRemoved`（IMachineLife）、`onUse`（潜行预览走 GT 原生 `showPreview`）、
  `connectorPositions()` / `getConnectorViews()` / `getCluster()`、status 菜单表面。
- `rebuildCluster` 改从 pattern 的 `MultiblockState` pos cache 汇总（**单一真相源，不再二次跑检测器**）：
  遍历 cache 得 bounds min/max、blockCount = 匹配格数；`MetaMachine.getMachine(level, pos)` 扫描收集
  GT 连接器机器位置；扫 STORAGE 块累加 `storageBytes`；扫接口块收集接口位置。构造与旧 cluster 同构的
  摘要（`AsyncSwitchCluster` / `AsyncProcessorCluster`），后续 `setStructuralFormed` / 连接器链接 /
  status 逻辑不变。
- `GTAsyncCrafting` 的 `.pattern { AsyncStructureGtPattern.build(type, it) }` 注册行不变（只换实现）。

### 1.7 Notifier 调整（`async/AsyncStructureNotifier.kt`）

- **保留** vanilla 分支：普通结构方块（frame/glass/tower/cores/cable）无 BE、无轮询，靠
  `onPlace`/`onRemove` → notifier → 附近普通控制器 `requestRescan`。
- **删** GT 分支（`MetaMachine.getMachine(...) is AsyncStructureGtControllerMachine →
  requestStructureCheck()`）：GT 控制器成形后由 LevelMixin 逐格失效（pos cache），未成形由 GT 异步
  轮询（每 4 tick）自动成形。

### 1.8 保持不变的部分

- §2 结构设计（`AsyncStructures` / `AsyncStructureDetector`）：形状常量不变；检测器保留给 vanilla +
  模块探测。
- §3 菜单/屏幕、§5 连接器、§6 注册胶水、§7 渲染/blockstate：全部不变。
- 连接器计数上限与检测器一致（见 §1.2 决策表），由谓词 max 限制表达。

### 1.9 新架构验证清单

- `:forge:compileKotlin` + `:fabric:compileKotlin`（含 `ae2isallyouneed.mixins.json` 加载）。
- 游戏内 Forge：搭 switch/processor（1..N bay）→ 成形；潜行+空手预览（第 0 页 = 1 bay）与 JEI 页
  （1..16,0）正常；拆任意结构块 → 立即失形（LevelMixin 逐格失效）；手动补齐 → ≤4 tick 成形；
  GT 终端 autoBuild → 默认放 1 组 bay；连接器网格链接 / status（storageBytes / blockCount）正确。
- 奇偶校验：同结构检测器路径与 GT pattern 路径结论一致（0..16、计数上限、玻璃/连接器替换格一致）。
- vanilla（无 GT）/ fabric 回归：不受影响。

### 1.10 新架构风险与取舍

- `@Overwrite` 三个方法体与 GTCEu 7.5.3 内部强耦合（版本已 pin 死，可接受；升级需逐行复查）。
- 新匹配代码异步线程安全要求严格：只经 `MultiblockState.update` 读世界。
- JEI 页面顺序 1..16,0（第 0 页是 1 bay 叠影，页码非升序）。
- cluster 摘要与 pattern 绑定：未来改 shape 需同步 `rebuildCluster` 的 cache 汇总逻辑。
- 检测器与 pattern 是两套判形实现（模块 / vanilla 仍走检测器）——保持 `AsyncStructures` 为唯一真相源，
  避免分叉。

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
- **GT TYPE 必须进 Forge 注册表**（`init/ForgeMenus.kt`）：`AsyncStructureGtStatusMenu.TYPE`
  类在首次右键时才加载，若只靠 AE2 `InitMenuTypes.queueRegistration`（AE2 加载期一次性 flush）
  会漏注册 → 客户端报 `Trying to open invalid screen with name:`。已在 `ForgeMenus` 用
  `MENUS.register("async_crafting_status_gt") { AsyncStructureGtStatusMenu.TYPE }`（带
  `isModLoaded("gtceu")` 守卫）提前注册。

---

## 4. GT 控制器（旧：检测器驱动，已实现；目标态见 §1.6）：`gt/AsyncStructureGtControllerMachine.kt`

- 抽象基类 `AsyncStructureGtControllerMachine : MultiblockControllerMachine, IInteractedMachine`；
  三个子类：`AsyncStructureGtProcessorMachine` / `AsyncStructureGtSwitchMachine` /
  `AsyncStructureGtFactoryMachine`。
- 工厂控制器没有结构锚（模块由接口探测），`detect()` 用
  `getPos().offset(2*facing.stepX, -4, 2*facing.stepZ)` 反推接口位置再 `detectModule`。
- `checkPattern()`：跑 `AsyncStructureDetector`，成功则把结构**全部 in-bounds 位置**（`cachePositions`）
  写入 `MultiblockState` pos cache（保证任意结构方块变化都能触发重检，不只角点）。
- `asyncCheckPattern()`：**不在异步线程碰世界** —— 检测延迟到主线程，在 `patternLock` 内执行
  `checkPatternWithLock() + onStructureFormed()`，并把状态注册进 `MultiblockWorldSavedData`。
- `requestStructureCheck()`：主线程立即重检的入口，供 `AsyncStructureNotifier` 调用（结构方块
  onPlace/onRemove 时），使手动搭建立即成形、成形后被拆立即失形并回到异步轮询。
- `onStructureFormed()`：重建 cluster，逐个连接器 `setHostController(this)`；
  `onStructureInvalid()`：逐个 `setHostController(null)` 并销毁 cluster。
- `onUse()`：镜像 `IMultiController.onUse` —— 未成形 + 潜行 + 空手 → 客户端调
  `MultiblockInWorldPreviewRenderer.showPreview`（其余情形照旧开 GT 菜单）。
- 真实 pattern（`gt/AsyncStructureGtPattern.kt`）：从 `AsyncStructures` 形状常量生成 base 形状
  （`FactoryBlockPattern.start()` 坐标：char=局部 x、row=局部 y、aisle=局部 z），每种 kind 一个
  具体谓词，必需空气用 `Predicates.air()`、任意格默认 any。仅供潜行预览 / JEI 页渲染，
  成形判定仍完全由检测器决定；`allowFlip(false)`。
- 预览朝向：GTCEu 1.20.1 的 `MultiblockInWorldPreviewRenderer` 对 EAST/WEST 的旋转与任何一致
  facing 约定相反（上游缺陷，GT 自家多方块同样受影响）——N/S 精确，E/W 显示为旋转 180°。

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
  - 同一组 flags + `IGridMultiblock` 收集同结构节点。

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
  .pattern { AsyncStructureGtPattern.build(type, it) }.allowFlip(false).register()`（kind → 结构类型：
  CONTROLLER→PROCESSOR、SWITCH→SWITCH、FACTORY→MODULE）。
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

## 7. 方块渲染 / blockstate 兼容（三种环境共用一份静态资源）

> 结论：`common/res` 的静态 blockstate（`common/resgen` 生成，`common/res` 是 gitignored 产物）
> 是**唯一**模型来源；GTCEu 的运行时机器模型生成只发生在 KubeJS 的
> `GTRegistryInfo.ALL_BUILDERS` 路径（`GregTechKubeJSPlugin`），我们的 GTRegistrate 机器不走那条
> 路，所以**静态 blockstate 就是权威**。因此 GT 方块必须让自己的 `StateDefinition` 属性与静态
> blockstate 的 variant key **完全一致**，否则渲染失败。

### 7.1 blockstate variant 解析规则（MC 1.20.1）

- 放置方块时按 `StateDefinition` 的属性组合在 blockstate 的 `variants` 里找 key；
- **key 与真实属性不一致 → 解析不到 variant → 模型缺失**（不显示/粉黑块），item 模型绕过 blockstate 所以正常；
- **key 包含方块不存在的属性 → 加载 blockstate 时崩溃**（blockstate 在资源加载期整体解析）；
- 无属性的方块（结构方块）→ key 为 `""`；
- 属性顺序无关（按名字匹配）。

### 7.2 根因：GT 多方块比 AE 方块多了 `upwards_facing`

- `MultiblockMachineBuilder` 构造器默认 `allowExtendedFacing(true)`（GTCEu 源码 line 64）→
  多方块额外获得 `GTBlockStateProperties.UPWARDS_FACING` 属性（`MetaMachineBlock.java` 83-86/149-151）；
- `MachineBuilder` 默认旋转 `NON_Y_AXIS` → `BlockStateProperties.HORIZONTAL_FACING`（`facing`）；
- 于是 GT 三方块的 `StateDefinition` 是 `facing` + `upwards_facing`（无 `formed`/`powered`），而静态
  blockstate 只写了 AE 风格的 8 键 `facing=*,formed=false/true` → 放下的控制器解析不到 variant → 缺模型。
- 连接器是普通 `MetaMachine`（非多方块），默认 `allowExtendedFacing=false`，但同样没有 `formed`/`powered`。

### 7.3 简单方案：让 GT 方块属性 = AE 方块属性（三环境共用静态资源）

- 控制器/连接器分两个具体方块类（镜像 AE 侧的 `AsyncStructureEntityBlock` / `AsyncStructureConnectorBlock`）：
  - `AsyncStructureGtMachineBlock`（控制器）：`super`（GT 的 `facing`）+ `FORMED`；
  - `AsyncStructureGtConnectorBlock`（连接器）：再追加 `POWERED`；
  - **必须用具体子类而不是派生字段区分**：`createBlockStateDefinition` 在 `super()` 构造链里被
    虚调用，此时任何实例字段都还没初始化 —— 若写 `connector = kind.role == ...` 的字段，那一刻读
    到的是 JVM 默认值 `false`，POWERED 会被静默丢掉，随后 init 里 `setValue(POWERED, false)` 直接抛
    `Cannot set property powered ... Block{minecraft:air}`（`Block{...}` 显示 air 只是因为方块还没注册，
    无关 air 本身）。GTCEu 自己规避这个陷阱的方式是读静态 `MachineDefinition.getBuilt()`。
  - 结果与 AE 侧完全一致：控制器 4×2=8 态，连接器 4×2×2=16 态。
- `GTAsyncCrafting.registerMachines`：
  - 控制器 `.allowExtendedFacing(false)` —— 去掉 `upwards_facing`，保证只暴露 `facing`；
  - 全部 `.simpleModel("block/async/<id>")` 替代原先的 `asyncStructureModel(...)`（那个 ModelInitializer
    只在 datagen 里生效，运行时毫无作用，纯死代码）。
  - `allowExtendedFacing(false)` 安全：GTCEu 所有读 `UPWARDS_FACING` 的地方都先判
    `isAllowExtendedFacing()`（`MetaMachine.getUpwardsFacing/setUpwardsFacing`、
    `MultiblockControllerMachine.setUpwardsFacing/onWrenchClick`），关闭后回退 `Direction.NORTH` 不崩。
- 成形翻转（与 AE 侧行为对齐）：`setBlock(pos, newState, Block.UPDATE_CLIENTS)` 只刷客户端、
  不通知邻居（无重扫循环），且先判 `current.block is AsyncStructureGtMachineBlock` 防止把已拆掉的方块复活：
  - 控制器：`onStructureFormed()` 置 `FORMED=true`，`onStructureInvalid()` 置 `false`（都在主线程）；
  - 连接器：`setHostController`/`onLoad` 里 `updateFormedState()` 置 `FORMED=isFormed()`；
    `POWERED` 属性为静态 key 匹配而存在，恒为 `false` 从不翻转（blockstate 里 `powered=true/false`
    映射到同一模型，翻转无视觉效果，故省略）。

### 7.4 三环境一致性

- **Forge+GT**：GT 机器带 `facing+formed(+powered)` → 静态 key 全部命中，成形翻转由检测器驱动；
- **Forge 无 GT / Fabric**：AE 方块本来就带这些属性，静态资源不变，天然工作；
- 静态资源无需为 GT 特判 —— 这就是"简单方案"的本质：**属性对齐而非按环境分支**。

---

## 8. 验证清单（旧架构，历史；新架构验证见 §1.9）

- **Forge 带 GT**：搭处理器（核心 + 扩展 bay + 模块）→ 成形；拆装扩展层（0..16）→ 重成形；
   右键 GT 控制器开 GT 菜单；接 ME 吞 32 频道；拆连接器 → 失形。
- **潜行+空手预览**：三个控制器（处理器/交换机/工厂）未成形时潜行右键 → 显示 base 结构叠影；
   朝向 N/S 精确，E/W 因上游渲染器缺陷旋转 180°（已知取舍）。
- **Forge 无 GT**：common 匹配器跑同一结构；`./gradlew :forge:compileKotlin` + 运行验证。
- **Fabric**：恒无 GT，common 匹配器回归。
- 三模块 `compileKotlin` / `build` 全绿。

---

## 9. 已知取舍与边界（旧架构，历史；新架构取舍见 §1.10）

- common 引用 GT 类 → fabric 编译 classpath 需 `compileOnly` GTCEu（含其内嵌 LDLib/Registrate
  的摊平解压）+ forge universal（`IForgeBlockEntity`）；运行期 fabric 绝不加载。
- `IMachineBlockEntity extends IForgeBlockEntity`（forge）—— common 只经构造参数间接用到，
  fabric 编译仍须 forge jar 在 classpath。
- GT pattern 只承担预览显示（不参与成形判定）：成形信息完全来自检测器，`MultiblockState` pos
  cache 只是让 GT 的重检触发机制（方块更新）能覆盖到结构 bounds。
- `checkPattern()` 在调用线程（可能是异步）同步跑 `detect()`（纯世界读）；`asyncCheckPattern()`
  把主线程任务放进 `patternLock` —— 若出现数据竞争再收紧。
- 扩展 0..16，每次 +6 深；超 16 自动拒绝；不检查结构外多余方块（与旧行为一致）。
- 级联：处理器 → LAN → 专用线缆 → 交换机 WAN；交换机 LAN 可再级联（`linkSwitches` 递归）。

---

## 10. 源码位置备忘

- GTCEu 7.5.3 源码：`/tmp/opencode/gtceu/src`
  - `api/pattern/{FactoryBlockPattern,BlockPattern,MultiblockState,MultiblockWorldSavedData}.java`
  - `api/pattern/TraceabilityPredicate.java`、`api/pattern/predicates/SimplePredicate.java`
    （`testGlobal`/`testLimited` 计数；`setMin/MaxGlobalLimited` 语义）
  - `api/machine/multiblock/MultiblockControllerMachine.java`（`asyncCheckPattern`/`onStructureFormed`/
    `patternLock`/`getMultiblockState`）
  - `api/machine/{MetaMachine,IMachineBlockEntity,MachineDefinition,MultiblockMachineDefinition}.java`
    （`MultiblockMachineDefinition.getMatchingShapes`/`repetitionDFS` —— mixin 目标）
  - `api/block/MetaMachineBlock.java`（`use` → `IInteractedMachine.onUse`；facing/upwards_facing 属性）
  - `api/block/property/GTBlockStateProperties.java`（`UPWARDS_FACING`/`NORTH_ONLY_FACING`/`VERTICAL_FACING`）
  - `api/registry/registrate/{MachineBuilder,MultiblockMachineBuilder}.java`（`simpleModel` 默认模板
    line 653/659-661；`allowExtendedFacing(true)` 在 MultiblockMachineBuilder line 64）
  - `common/data/models/GTMachineModels.java`（`createBasicMachineModel`；运行时模型生成仅 KJS 路径）
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
  - （目标态新增）`common/.../mixin/gtceu/{BlockPatternGroupedMixin,MultiblockMachineDefinitionGroupMixin}.java`
     与 `common/.../gt/IGroupedBlockPattern.kt`；注册进 `common/src/main/resources/ae2isallyouneed.mixins.json`，
     `plugin` = `allyouneed.mixin.MyMixinPlugin`（通用可选依赖守卫）
  - `forge/.../{ExampleMod,init/ForgeBlocks,init/GTAsyncCrafting,forge/client/ForgeClientEvents}`
  - `fabric/.../init/FabricBlocks`
