# AE2 网络体系知识库

> 面向本模组（`ae2isallyouneed`）开发：AE2 15.4.x 网络如何工作、节点如何标识、
> 以及本模组如何在其上叠加 48-bit MAC。本仓库是 **AE2 附属模组**（Mixin），不是 fork。
>
> 合成计算 / 后台调度 / 库存快照见 **[Crafting-Calculation.md](./Crafting-Calculation.md)**。

---

## 1. 核心对象

| 层 | 类型 | 包 / 类 | 作用 |
| --- | --- | --- | --- |
| API | `IGridNode` | `appeng.api.networking` | 只读节点视图 |
| API | `IManagedGridNode` | 同上 | Host 侧生命周期包装 |
| API | `IGrid` | 同上 | 一个连通的 ME 网络 |
| API | `IGridConnection` | 同上 | 两节点之间的边 |
| 实现 | `GridNode` | `appeng.me` | 真实节点 |
| 实现 | `InWorldGridNode` | `appeng.me` | 带世界坐标与暴露面的节点 |
| 实现 | `ManagedGridNode` | `appeng.me` | 创建 / 销毁真实节点 |
| 实现 | `Grid` | `appeng.me` | 网络图 + 服务容器 |
| 实现 | `GridConnection` | `appeng.me` | 边实现 |

Host 常见形态：

- `AENetworkBlockEntity` / `AENetworkInvBlockEntity` / `AENetworkPowerBlockEntity`
- `AEBasePart`（线缆总线上的部件）
- 外部模组（如 GTCEu `GridNodeHolder`）

典型持有关系：

```text
Host (BE / Part)
  └─ IManagedGridNode  (ManagedGridNode)
       └─ create(level, pos) → GridNode | InWorldGridNode
            └─ markReady() → 邻接发现 → GridConnection → Grid
```

---

## 2. 节点身份（原版 AE2）

**AE2 没有稳定的设备级网络地址**（无 MAC、无 node UUID）。

| 字段 / 手段 | 含义 | 跨存档稳定？ |
| --- | --- | --- |
| `owner` | Host 对象引用 | 仅加载期内 |
| `owningPlayerId` (`"p"`) | 安全系统玩家 id | 是（玩家，非设备） |
| `getOwningPlayerProfileId()` | Mojang UUID | 是（玩家） |
| `myGrid` | 当前网络 | 否（合并 / 分裂） |
| `InWorldGridNode` 坐标 | 方块位置 | 仅位置语义 |
| `Grid.serialNumber` | 调试计数 | 否，不落盘 |
| `IGrid.getMachines(Class)` | 按 Host 类型枚举 | 非寻址 |

节点 NBT（`GridNode.saveToNBT`）几乎为空：玩家 id + 各 grid service 的 per-node 数据。  
默认 BE 节点 tag 名：`"proxy"`（`AENetworkBlockEntity`）；Part 默认 `"gn"`。

查找设备靠：**对象引用、Host 类型、节点服务、世界坐标**，不是地址。

---

## 3. 拓扑与入网

### 3.1 生命周期

1. Host 构造时创建 `IManagedGridNode`，配置 flags / services / 暴露面 / 闲置耗电。
2. 服务端 `onReady`（或等价路径）调用 `mainNode.create(level, pos)`。
3. `InWorldGridNode.findInWorldConnections()` 扫描暴露面，经 `GridHelper.getExposedNode`，检查线缆颜色。
4. `GridConnection.create(a, b, dir)`：
   - 皆无 grid → `Grid.create(a)` 再吸收 `b`
   - 一方有 grid → 另一方加入
   - 双方有 grid → 高优先级吸收低优先级（`GridPropagator`）
5. `PathingService` / `PathingCalculation` 从控制器沿缆树分配 **频道**。
6. 节点在通电 + 频道满足 + grid boot 后 `isActive()`。

### 3.2 连接类型

| 类型 | 机制 |
| --- | --- |
| 世界邻接 | 暴露面 + 颜色 + `IInWorldGridNodeHost` |
| 同 Host 内部 | 无方向的额外连接（线缆总线多 Part） |
| 远程 | 量子桥、ME P2P（outer / inner 节点） |
| API 直连 | `GridHelper.createConnection(a, b)` |

### 3.3 频道相关 `GridFlags`

- `REQUIRE_CHANNEL`：设备需要频道  
- `DENSE_CAPACITY`：致密容量（32）  
- `CANNOT_CARRY` / `CANNOT_CARRY_COMPRESSED`：不能传频道  
- `MULTIBLOCK` + `IGridMultiblock`：多方块共享一条频道路径  
- `PREFERRED`：优先路径（智能缆等）

### 3.4 “通信”方式

**没有按地址发包的帧协议。**  
节点注册 `IGridNodeService`；网格级服务聚合它们：

- `IStorageService` — 存储  
- `ICraftingService` — 合成  
- `IEnergyService` — 能量  
- `IPathingService` — 频道 / boot  
- `ITickManager` — 设备 tick  
- `P2PService` — P2P（**16-bit frequency** 配对）

业务代码：`node.grid.getStorageService()` 等，或实现被 grid 轮询的 service。

---

## 4. 原版中接近“地址”的概念

| 概念 | 宽度 | 范围 | 说明 |
| --- | --- | --- | --- |
| 玩家 id | `int` | 每节点 | 安全 |
| P2P frequency | `short` | 隧道对 | 最接近“信道地址” |
| Grid inventory serial | `long` | 终端同步 | 映射 `AEKey`，非设备 |
| 线缆颜色 | `AEColor` | 连接过滤 | 非唯一 |
| 本模组维度元件 id | 24-bit | 存储元件 | 世界级分配先例 |

---

## 5. 拆除 / 放置与 NBT

### 5.1 方块（BE）

| 路径 | 行为 |
| --- | --- |
| 扳手拆除 | `AEBaseBlockEntity.disassembleWithWrench` → `exportSettings(DISMANTLE_ITEM)` 写入掉落物 |
| 普通破坏 | `AEBaseEntityBlock.getDrops` → 同样 `exportSettings(DISMANTLE_ITEM)` |
| 放置 | `AEBaseEntityBlock.setPlacedBy` → `importSettings(DISMANTLE_ITEM, itemTag)` |

**注意：普通破坏与扳手都会 export settings**，不能仅靠 `exportSettings` 区分“挖掉丢弃 / 扳手保留”。

### 5.2 Part（线缆总线）

| 路径 | 行为 |
| --- | --- |
| 扳手拆 Part | `addPartDrop(drops, wrenched=true)` |
| 破坏总线 | `addPartDrop(drops, wrenched=false)` |
| 放置 | `PartPlacement.placePart` → `importSettings(DISMANTLE_ITEM, configTag)` |

`IPart.addPartDrop` 内部仍调用 `exportSettings(DISMANTLE_ITEM)`，但 **`wrenched` 参数可用于分支**。

### 5.3 记忆卡

`SettingsFrom.MEMORY_CARD`：配置复制，**不应**复制设备身份（MAC）。

### 5.4 节点 NBT 陷阱

`GridNode.saveToNBT` 在 `myGrid == null` 时会 **remove** 整个节点 tag。  
跨 unload 的自定义字段必须以 **`ManagedGridNode` 为权威缓存**，在 `saveToNBT` 末尾兜底写回。

---

## 6. 本模组已有相关注入

| 文件 | 作用 |
| --- | --- |
| `mixin/GridNodeMixin.java` | 每节点运行时状态（异步吞频道计数） |
| `mixin/PathingCalculationMixin.java` | 成形连接器吞满频道 |
| `async/AsyncChannelNodeHolder.java` | Mixin 接口 |
| `cell/dimensional/DimensionalCellStore.kt` | 世界级唯一 ID 分配 + 落盘先例 |

异步连接器：`GridFlags.MULTIBLOCK | REQUIRE_CHANNEL | DENSE_CAPACITY` + `IGridMultiblock`。

---

## 7. 本模组 MAC 地址层

设计目标见 README「IP / MAC 地址」。实现要点：

| 项 | 决策 |
| --- | --- |
| 宽度 | 48-bit，存为 `long & 0xFFFF_FFFF_FFFFL` |
| 粒度 | **每个 `GridNode` 一个**（设备节点） |
| 覆盖 | 全部 AE2 设备节点；**线缆 `ICablePart` / `CablePart` 不分配** |
| 分配 | 世界级 `MacAddressRegistry`（类比维度元件） |
| 冲突 | live 表一对一：`register` 拒绝抢占；NBT/扳手恢复冲突时 **重新分配** |
| 权威存储 | `ManagedGridNode` 字段 + 节点 NBT key `ayn_mac` |
| 扳手 | 写入物品 `allyouneed_macs`（tagName → mac） |
| 挖掉 | 不写 / 不带 MAC → 重放后重新分配 |
| 记忆卡 | 不复制 MAC |
| 与 AE2 关系 | **正交**：不改变频道 / 存储 / 合成；供机器人绑定、网络图、调试 |

主要代码：

```text
common/.../mac/
  MacAddress.kt
  MacAddressRegistry.kt
  MacNbt.kt
  IMacAddressHolder.java
  IManagedMacAddressHolder.java
common/.../mixin/
  GridNodeMixin.java          # MAC 运行时字段 + NBT
  ManagedGridNodeMixin.java   # 持久缓存 + 分配 + lookup 注册
  AEBaseBlockEntityMixin.java # 扳手掉落写入 MAC
  AEBaseEntityBlockMixin.java # 放置时恢复 MAC
  IPartMixin.java             # Part 掉落 wrenched 分支
  PartPlacementMixin.java     # Part 放置恢复 MAC
```

查找：`MacAddressRegistry.lookup(mac) → IGridNode?`（弱引用，unload 后失效）。

---

## 8. 多节点 Host

| Host | 节点 | tagName 示例 |
| --- | --- | --- |
| 普通网络 BE | `mainNode` | `proxy` |
| 普通 Part | `mainNode` | `gn` |
| ME P2P | main + `outerNode` | 默认 + 部件自定 |
| Quartz Fiber / Toggle Bus | main + outer | 同上 |

物品 NBT 用 **tagName → mac** 映射，重放时写回对应 `ManagedGridNode`。

---

## 9. 边界与限制

- 非 AE2 标准拆除路径的模组设备：世界内 MAC 仍稳定；扳手保留取决于是否走 AE dismantle API。  
- 结构方块 / 复制模组可能导致 MAC 冲突：加载时以 NBT 为准，lookup 仅登记存活节点。  
- 客户端无真实节点（`create` 在 client 空操作）；UI 显示需另行同步。  
- MAC **不会**接入 AE2 pathing；点对点协议需自建。

---

## 10. 调试备忘

- 节点 `toString` 含 Java 身份，非 MAC。  
- 控制器自身在本模组异步结构里 **可以不是** grid 节点；只有连接器上 ME 网。  
- 改节点 NBT 且节点已 ready 时，AE2 可能 destroy + markReady 重入网（`areTagsEqualIgnoringPlayerId`）。
