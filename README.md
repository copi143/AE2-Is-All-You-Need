# AE2 Is All You Need

- $5$ 个不同大小的元件实在太少了，加到 $20$ 个！从 $1 \text{Ki}$ 到 $256 \text{Ti}$ 全覆盖。
- 原版 AE2 只能显示 $9.2 \text{E}$ 的物品或 $9.2 \text{P}$ 的流体，后期终端内大量超过显示上限的物品，这个模组移除了显示上限，让你能看到你的网络到底存储了多少物品。
- 大型科技包的完结任务物品合成起来真的太慢了，这个模组提供了 $1 \text{tick}$ 合成任何物品的方案，让你能够批发任何物品。

> 提及容量数字时请注意 [SI 词头](https://zh.wikipedia.org/wiki/%E5%9B%BD%E9%99%85%E5%8D%95%E4%BD%8D%E5%88%B6%E8%AF%8D%E5%A4%B4) 与 [IEC 词头](https://zh.wikipedia.org/wiki/%E4%BA%8C%E9%80%B2%E4%BD%8D%E5%89%8D%E7%BD%AE%E8%A9%9E) 的区别。

## 模组关系

### 必选依赖

- `Kotlin for Forge`
- `Fabric Language Kotlin`
- `GuideME` *TODO*
- `Applied Energistics 2`

### 可选依赖

- `Just Enough Items`
- `EMI`
- `Jade` *TODO*
- `GregTechCEu Modern`

### 兼容模组

- `Mekanism` *TODO*
- `Create` *TODO*

### 注入资源

- `FTB Quests` *TODO*

### 已知问题

- Fabric 无法正常启动

### 感谢

- `Minecraft Forge`
- `Fabric Loader`
- `NeoForge`
- `Applied Energistics 2`
- `ExtendedAE`
- `AdvancedAE`
- `GregTechCEu Modern`
- `BuildCraft`
- `PackagedAuto`

## 修改列表

### 存储

- [ ] `移除` 创造 ME 物品元件、创造 ME 流体元件
- [x] `添加` 创造 ME 存储元件
- [x] `添加` 维度存储元件

### 合成

- [ ] `替换` 原版合成存储器为模组物品，保持字节数。
- [x] `添加` 合成存储器 $1 \text{Ki}$ ~ $256 \text{Ti}$
- [x] `添加` 创造合成存储器

### 能源

- [ ] `替换` 能源元件 $\Rightarrow$ $4 \text{Ki}$ 能源元件
- [ ] `替换` 致密能源元件 $\Rightarrow$ $64 \text{Ki}$ 能源元件
- [ ] `替换` 创造能源元件改为本模组物品
- [x] `添加` 能源元件 $1 \text{Ki}$ ~ $256 \text{Ti}$
- [x] `添加` 自供能能源元件 $1 \text{Ki}$ ~ $256 \text{Ti}$

每字节对应 $64 \text{AE}$ 的存储容量。

- $4 \text{Ki}$ 能源元件能存储 $4 \text{Ki} \times 64 \text{AE} = 256 \text{KiAE}$ 的能量，与原版能源元件类似。
- $64 \text{Ki}$ 能源元件能存储 $64 \text{Ki} \times 64 \text{AE} = 4 \text{MiAE}$ 的能量，比原版致密能源元件更多。

自供能能源元件 $1 \text{tick}$ 恢复其总容量 $\frac{1}{1024}$ 的能量。

- [ ] 当检测到 `GregTechCEu Modern` 模组已加载时，允许在能源元件上放置避雷针，一旦避雷针被雷击则下方的能源元件立即充满能量。

> 这是什么意思呢，我不知道啊 \[doge\]

### 材料

- [ ] `奇点合金`、`奇点合金块`：一种高级材料。

#### 物质球、奇点

- [ ] 做起来太简单的奇点肯定是不行的，原版的 `物质球` 改为 `物质球 T2`，`奇点` 改为 `奇点 T0`。

$$ 1 \space \text{物品} \Rightarrow 1 \space \text{物质球}_{\text{T0}} $$
$$ 64 \space \text{物质球}_{\text{T0}} \Rightarrow 1 \space \text{物质球}_{\text{T1}} $$
$$ 64 \space \text{物质球}_{\text{T1}} \Rightarrow 1 \space \text{物质球}_{\text{T2}} $$
$$ 64 \space \text{物质球}_{\text{T2}} \Rightarrow 1 \space \text{物质球}_{\text{T3}} $$
$$ 64 \space \text{物质球}_{\text{T3}} \Rightarrow 1 \space \text{物质球}_{\text{T4}} $$
$$ 1 \space \text{物质球}_{\text{T4}} \Rightarrow 1 \space \text{奇点}_{\text{T0}} $$
$$ 64 \space \text{奇点}_{\text{T0}} \Rightarrow 1 \space \text{奇点}_{\text{T1}} $$
$$ 64 \space \text{奇点}_{\text{T1}} \Rightarrow 1 \space \text{奇点}_{\text{T2}} $$
$$ 64 \space \text{奇点}_{\text{T2}} \Rightarrow 1 \space \text{奇点}_{\text{T3}} $$
$$ 64 \space \text{奇点}_{\text{T3}} \Rightarrow 1 \space \text{奇点}_{\text{T4}} $$
$$ 1 \space \text{奇点}_{\text{T4}} \Rightarrow 1 \space \text{压缩奇点} $$

| 物品     | 原版消耗 | 模组消耗        |
|----------|----------|-----------------|
| 物质球   | 256      | 4096            |
| 奇点     | 256000   | 16777216        |
| 压缩奇点 | -        | 281474976710656 |

$256 \text{Ti}$ 物品压成一个压缩奇点！

- [ ] 多方块物质聚合器。

## 新增概念

完全做完可用了会打勾：

- [ ] 机器样板
- [ ] 异步合成
- [ ] 机器人

### 机器样板 (Machine Pattern)

`分子装配室` 经过了大幅升级，已经不只能处理工作台配方了，其可以处理任何注册的机器的配方，但需要将机器塞入分子装配室的机器槽位中。

两种配方注册方式：

- [ ] 注册配方类别，自动从 `JEI` / `EMI` 拉取
- [ ] 手动创建配方组并添加配方

#### 概率修正

- [ ] 真随机是不存在的，所有概率配方自动倍增为普通配方。

原始配方：$3 \text{A} \Rightarrow 2 \text{B}_{35\%}$

修改配方：$60 \text{A} \Rightarrow 14 \text{B}$

此处保证 $3$ 为 $60$ 的因数，$2$ 为 $14$ 的因数，故不会除以 $2$。

#### 打包多方块

- [ ] `PackagedAuto` 遗憾离场，你封配方我们直接封多方块。

### 合成计划字节数计算

AE2 的字节数计算太复杂了，本模组添加的异步合成使用非常简单的公式：

1. 统计所有产物、中间产物、原料的总数量 $x$
2. 统计所有产物、中间产物、原料的总类型数 $y$
3. 字节数 $B = x + y^3$

> 注意当前仍然没有实现

### 委托合成

```text
ME 请求 → AE CraftingService 算树
   ├─ 树节点是异步配方（机器样板@模块 + 对应机器在场）
   │     → 异步处理器在本 tick 内瞬间执行该节点
   ├─ 异步计算遇到普通配方
   │     → 委托合成：生成一个"虚拟普通合成 CPU"插入 ME 网络
   │        （类似 AdvancedAE 量子处理器）按常规速度执行，异步任务等待其完成
   └─ 普通计算遇到异步配方
         → 委托合成：异步处理器生成"伪样板"让 AE 能存完整配方；
            AE 执行该伪样板时再委托回异步处理器
```

> 注意当前仍然没有实现

### 异步合成 (Async Crafting)

将合成任务委托给 `异步合成处理器`，它会控制 `异步合成模块` 在完成合成，任意复杂的合成树执行时间均为 $1 \text{tick}$，合成速度只受限于 AE 本身计算合成树的速度。

三个相关多方块结构：

> 此处同时介绍“游戏设定”与“代码实现”，注意区分其不同之处。

- `异步合成处理器`：控制异步合成模块的工作，接收合成请求，计算合成树，分配任务。
- `异步合成网络交换机`：扩展异步合成处理器的网络，允许大量异步合成模块协同工作。
- `异步合成模块`：存放样板并执行合成任务，输入输出全部在 `异步合成网络` 中完成，不支持物品输入输出；实际的代码中合成任务由 `异步合成处理器` 进行处理，`异步合成模块` 只负责提供样板数据。

<details>
  <summary>包含以下方块</summary>

- `异步合成机器框架`：三个多方块结构的基础框架。
- `异步合成机器方块`：三个多方块结构的地板、墙壁。
- `异步合成机器玻璃`：可以替换 `异步合成机器方块` 作为墙壁的透明方块，不能作为地板。
- `奇点合金加固空间塔`：三个多方块结构内部的框架结构。
- `异步合成能源核心`：为三个多方块结构提供能源。
- `异步合成计算核心`：在 `异步合成处理器` 和 `异步合成网络交换机` 内部的方块。
- `异步合成存储核心`：在 `异步合成处理器` 中为其提供缓存容量。
- `异步合成执行核心`：在 `异步合成模块` 中执行合成任务的方块。
- `异步网络控制器`：`异步合成处理器` 的核心方块。
- `异步网络交换机`：`异步合成网络交换机` 的核心方块。
- `异步合成工厂`：`异步合成模块` 的核心方块。
- `异步合成专用线缆`：连接 `异步合成处理器` 与 `异步合成网络交换机`，类似于 `ME 线缆`，但不能分叉且不支持传输频道。
- `异步合成 ME 连接器`：将 `异步合成处理器` 连接到 `ME 网络`，多方块结构中只能存在一个，需要 32 个频道以便工作。
- `异步合成 WAN 连接器`：安装在 `异步合成网络交换机` 上，将其连接到 LAN 口，多方块结构中只能存在一个，必须使用 `异步合成专用线缆` 一一对应连接。
- `异步合成 LAN 连接器`：安装在 `异步合成处理器` 或 `异步合成网络交换机` 上，多方块结构中可以存在 0 到 2 个。
- `异步合成模块安装接口`：在 `异步合成处理器` 或 `异步合成网络交换机` 指定位置安装的接口，允许在其上建造 `异步合成模块`。

`异步合成 ME 连接器`、`异步合成 WAN 连接器`、`异步合成 LAN 连接器` 注册为特殊的格雷仓室。

</details>

#### 异步合成网络

整个网络只能存在一个 `异步合成处理器`、多个 `异步合成网络交换机` 和多个 `异步合成模块`。`异步合成网络交换机` 扫描挂载在自身上的 `异步合成模块`；`异步合成处理器` 扫描直接或级联连接的 `异步合成网络交换机` 及其挂载的 `异步合成模块`。大多数代码内操作都由 `异步合成处理器` 承担，避免额外计算。

#### 多方块结构

> [!WARNING]
> 不要自己搭建多方块结构，用 GT 的终端！

<details>
  <summary>详细定义</summary>

- `F` = `异步合成机器框架`
- `B` = `异步合成机器方块` / `异步合成机器玻璃` (底面不可以替换)
- `G` = `异步合成机器玻璃`
- `T` = `奇点合金加固空间塔`
- `E` = `异步合成能源核心`
- `S` = `异步合成存储核心`
- `P` = `异步合成计算核心` / `异步合成执行核心` (按照多方块类型选择)
- `C` = `异步网络控制器` / `异步网络交换机` / `异步合成工厂` (按照多方块类型选择)
- `Z` = `异步合成模块安装接口`

**异步合成模块** ($3 \text{宽} \times 7 \text{高} \times 5 \text{深}$)：

*正面* (一个 $3 \times 7$ 的矩形)：

```text
FFF
FBF
FBF
FCF
FBF
FBF
FFF
```

*左/右侧面* (一个 $5 \times 7$ 的矩形)：

```text
FFFFF
FBBBF
FBBBF
FBBBF
FBBBF
FBBBF
FFFFF
```

*侧面内部* (一个 $5 \times 7$ 的矩形)：

```text
FBBBF
BTETB
BTETB
BTPTB
BTETB
BTETB
FBBBF
```

底面中间方块连接 `异步合成模块安装接口` 以安装！

异步合成模块横向建造在 `异步合成处理器` 或 `异步合成网络交换机` 上：模块 $3 \times 7 \times 5$ 中深度 $5$ 的边对应结构 $19$ 宽的边，左右各放置一个；左侧模块的核心方块（`异步合成工厂`）朝左，右侧的朝右，方向错误不识别。

**异步合成网络交换机** ($19 \text{宽} \times 7 \text{高} \times 11 \text{深}$ 深度按照重复数量增加)：

`核心结构` 中的 `异步合成机器方块` 可以替换为 `异步合成 WAN 连接器` 或 `异步合成 LAN 连接器`。

*正面* (一个 $13 \times 5$ 的矩形 `核心结构` + 下方两层大地板)：

```text
FFFFFFFFFFFFF
FBBBBBBBBBBBF
FBBBBBCBBBBBF
FBBBBBBBBBBBF
FFFFFFFFFFFFF
大地板 (下面说明)
大地板 (下面说明)
```

*左/右侧面* (一个 $5 \times 5$ 的正方形 `核心结构` + 下方两层大地板)：

```text
FFFFF
FBBBF
FBBBF
FBBBF
FFFFF
大地板 (下面说明)
大地板 (下面说明)
```

*侧面内部* (一个 $5 \times 5$ 的正方形 `核心结构` + 下方两层大地板)：

内部 11 片完全一样的结构。

```text
FBBBF
BTETB
BEPEB
BTETB
FBBBF
大地板 (下面说明)
大地板 (下面说明)
```

*大地板* (一个 $17 \times 9$ 的矩形和一个 $19 \times 11$ 的矩形)：

`核心结构` 直接建在 `大地板` 上，`异步合成模块安装接口` 嵌入在 `大地板` 上。

上层地板 (标 X 的地方是 `异步合成机器方块` 但是被 `核心结构` 盖住了)：

```text
FFFFFFFFFFFFFFFFF
FBBBBBBBBBBBBBBBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBBBBBBBBBBBBBBBF
FFFFFFFFFFFFFFFFF
```

地板是实心的，标 X 只是为了清晰，实际搭建也是使用 `异步合成机器方块`！

下层地板比上层大一圈，完全由 `异步合成机器方块` 加一圈 `异步合成机器框架` 组成，过于简单不需要画图。

*可重复模块* (大地板也要扩大，每次扩大 $6$ 格)：

这边展示上层地板，下层永远比上层大一圈，并且全部都是 `异步合成机器方块` 填充中心。

```text
FFFFFFFFFFFFFFFFF
FBBBBBBBFBBBBBBBF
FBXXXXXBFBXXXXXBF
FBXXZXXBFBXXZXXBF
FBXXXXXBFBXXXXXBF
FBBBBBBBFBBBBBBBF
FTTTTTTTTTTTTTTTF
FBBBBBBBBBBBBBBBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBBBBBBBBBBBBBBBF
FFFFFFFFFFFFFFFFF
```

地板是实心的，标 X 只是为了清晰，实际搭建也是使用 `异步合成机器方块`！

`核心结构` 的后方，一次扩展左右两个安装空间，可以继续扩展：

```text
FFFFFFFFFFFFFFFFF
FBBBBBBBFBBBBBBBF
FBXXXXXBFBXXXXXBF
FBXXZXXBFBXXZXXBF
FBXXXXXBFBXXXXXBF
FBBBBBBBFBBBBBBBF
FTTTTTTTTTTTTTTTF
FBBBBBBBFBBBBBBBF
FBXXXXXBFBXXXXXBF
FBXXZXXBFBXXZXXBF
FBXXXXXBFBXXXXXBF
FBBBBBBBFBBBBBBBF
FTTTTTTTTTTTTTTTF
FBBBBBBBBBBBBBBBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBBBBBBBBBBBBBBBF
FFFFFFFFFFFFFFFFF
```

**异步合成处理器** ($19 \text{宽} \times 15 \text{高} \times 19 \text{深}$ 深度按照重复数量增加)：

类似 `异步合成网络交换机`，但核心结构是一个正方体，下方是两次扩展 (四个模块) 的样子。`核心结构` 中最外层的 `异步合成机器方块` 可以替换为 `异步合成 ME 连接器` 或 `异步合成 LAN 连接器`（`异步合成 ME 连接器` 只能存在一个）。

```text
FFFFFFFFFFFFFFFFF
FBBBBBBBFBBBBBBBF
FBXXXXXBFBXXXXXBF
FBXXZXXBFBXXZXXBF
FBXXXXXBFBXXXXXBF
FBBBBBBBFBBBBBBBF
FTTTTTTTTTTTTTTTF
FBBBBBBBFBBBBBBBF
FBXXXXXBFBXXXXXBF
FBXXZXXBFBXXZXXBF
FBXXXXXBFBXXXXXBF
FBBBBBBBFBBBBBBBF
FTTTTTTTTTTTTTTTF
FBBBBBBBBBBBBBBBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBXXXXXXXXXXXXXBF
FBBBBBBBBBBBBBBBF
FFFFFFFFFFFFFFFFF
```

*六个面* (一个 $13 \times 13$ 的正方形)：

```text
FFFFFFFFFFFFF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FBBBBBCBBBBBF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FBBBBBBBBBBBF
FFFFFFFFFFFFF
```

这个 `C` 只在正面，其余面为 `B`。

*内部一圈六个面* (一个 $11 \times 11$ 的正方形)：

```text
FTTTTTTTTTF
TEEEEEEEEET
TE       ET
TE       ET
TE       ET
TE       ET
TE       ET
TE       ET
TE       ET
TEEEEEEEEET
FTTTTTTTTTF
```

*再内部一圈六个面* (一个 $9 \times 9$ 的正方形)：

```text
FTTTTTTTF
T       T
T       T
T       T
T       T
T       T
T       T
T       T
FTTTTTTTF
```

*最内部* 一个 $5 \times 5$ 的正方体，表面使用 `异步合成计算核心` 填充，内部使用 `异步合成存储核心` 填充。

处理器上层地板为 $17 \times 17$，下层地板为 $19 \times 19$。$9 \times 9$ 内圈与 $5 \times 5$ 核心立方体之间是深度 1 格的 $7 \times 7$ 空气层，$5 \times 5$ 计算/存储核心立方体悬浮在空腔中。

</details>

### ME 无线供能 (ME Wireless Power Transmission)

- [ ] 安装了 `无线供能升级` 的无线访问点可以自动向周围的接入设备供能，也可以选择为所有附近设备供能无论其是否接入此网络。

- [ ] 通过奇点连接的设备会自动受到 ME 网络的供能，并且尽可能快地供满电量，网络能量充足时 $1 \text{tick}$ 内直接充满。

### 机器人 (Robot)

> 实际上代码内叫“ME 移动工作单元” (ME Mobile Worker)

AE2 的所有操作都只能对紧邻的方块进行，如果想要在一定范围内进行操作呢，希望飞在天上机器人能解决这个问题。

> 我不知道是哪个模组的机器人鸽到现在没更新啊。

TODO:

- [ ] 机器人
- [ ] 机器人转接坞
- [ ] TF 卡

机器人移动与执行动作都会扣除自身的能量，为了不耗尽能量可以定期回到转接坞充能或使用 ME 无线供能机制。

当机器人停靠在机器人转接坞上时，转接坞会自动消耗 ME 网络的能量给机器人充能。

与某个~~不存在的~~模组不同，机器人并不绑定到转接坞，而是绑定到 ME 无线接入点或量子环，如果机器人失去与 ME 网络的连接则会进入休眠状态，缓慢下落至地面并等待再次连接 ME 网络。

机器人程序更新的方法有三种：

- 物理接触更新，玩家直接打开机器人 UI 在内部替换 TF 卡
- 转接坞自动更新，可以在转接坞内插入一张 TF 卡并设置为当机器人连接时自动写入到机器人。
- ME 网络远程更新，在同一网络的其它设备（包括其它机器人）均可以远程控制程序更新。

机器人有 9 格快捷栏（其中一格为主手）和 1 格副手，没有任何其它本地存储空间，也无法扩展现有的本地存储空间，但机器人可以访问连接的 ME 网络内的所有内容。

大多数情况下机器人每 tick 只能对世界做出一次操作，但可以做出多次观测，物品存取速度差不多就是 $64 \space \text{item} \cdot \text{tick}^{-1}$，因为一次只能拿一组。

右键机器人打开 UI，内部有多个按钮：

- 关机：关闭机器人，关机后的机器人会缓慢下落至地面。
- 重启：重置机器人内部的运行状态。
- 暂停/继续：让机器人暂停工作并悬停在当前位置。
- 充电：让机器人回到最近的转接坞充电。

下面描述一些我认为或许有用的场景。

#### AE 树场 / 农场

#### 自动打怪 / 自动插火把

#### 异常检查

### IP / MAC 地址

每个 AE2 **网络节点**（`GridNode`）拥有一个 **48-bit 全局唯一 MAC**，用于稳定标识设备，供机器人远程绑定、网络图 / 调试元数据，以及基地搬迁后无需重绑无线设备。

| 项 | 说明 |
| --- | --- |
| 格式 | 48-bit，显示为 `XX:XX:XX:XX:XX:XX`（本地管理位） |
| 粒度 | 每个设备 `GridNode` 一个（多 Part、P2P outer 等各自独立） |
| 覆盖 | 全部 AE2 **设备**节点；**线缆不分配 MAC** |
| 冲突 | 同一时刻一个 MAC 只对应一个存活节点；复制/冲突时自动重分配 |
| 分配 | 世界存档级注册表，首次入网时分配 |
| 扳手拆除 | **保留** MAC，写入掉落物 NBT，重放后恢复 |
| 普通挖掉 | **不保留**，再次放置分配新 MAC |
| 记忆卡 | 不复制 MAC |
| 与 AE2 | 不改变频道 / 路由 / 存储；纯附加身份层 |

实现与 AE2 网络背景见 [`docs/AE2-Network.md`](docs/AE2-Network.md)。

- [x] 每节点 48-bit MAC 分配与世界持久化
- [x] 扳手保留 / 挖掉丢弃
- [x] 按 MAC 查找存活节点 API（`MacAddressRegistry`）
- [x] Jade / WTHIT / TOP 显示（AE2 igtooltip）+ 扳手物品 tooltip
- [ ] 机器人与远程控制绑定

## 版权

- 所有代码为 All Rights Reserve。
- 所有本地化文本使用 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) 许可分发。
- 所有纹理资源以 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) 许可分发。
  - 部分纹理资源衍生自 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/forge/1.20.1)，原许可协议为 [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)。
