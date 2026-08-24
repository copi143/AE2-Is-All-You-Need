# AE2 Is All You Need

AE2 Is All You Need 是一个以在整合包末期继续提升产能为目标的应用能源模组扩展，将会在解决大型科技整合包后期规模爆炸与合成效率的痛点。添加更大的存储元件并移除显示数量上限；机器样板系统让分子装配室直接执行任何配方；通过全新的异步合成体系让任意复杂的合成树在 $1 \text{tick}$ 内完成。为后期玩家提供批发一切的能力。

> 提及容量数字时请注意 [SI 词头](https://zh.wikipedia.org/wiki/%E5%9B%BD%E9%99%85%E5%8D%95%E4%BD%8D%E5%88%B6%E8%AF%8D%E5%A4%B4) 与 [IEC 词头](https://zh.wikipedia.org/wiki/%E4%BA%8C%E9%80%B2%E4%BD%8D%E5%89%8D%E7%BD%AE%E8%A9%9E) 的区别。

---

> [!WARNING]
> 仍然无法游玩（配方都没添加呢）。初步可游玩后会发布 `0.0.1` 版本。

---

> [!WARNING]
> 已知问题：
>
> - Fabric 无法正常启动
> - 多方块结构成型检测异常

---

文档状态：

- [Async-Crafting-MultiBlock](docs/Async-Crafting-MultiBlock.md) 纯手写
- [Robot](docs/Robot.md) 半手写半AIGC
- [AE2-Network](docs/AE2-Network.md) 纯AIGC
- [Compose-Framework](docs/Compose-Framework.md) 纯AIGC
- [Crafting-Calculation](docs/Crafting-Calculation.md) 纯AIGC
- [GT-Multiblock-Integration](docs/GT-Multiblock-Integration.md) 纯AIGC
- [Http-Server](docs/Http-Server.md) 纯手写

---

## 依赖关系

<details>
  <summary><h3>模组关系</h3></summary>

1. 必选依赖
   - `Kotlin for Forge`
   - `Fabric Language Kotlin`
   - `GuideME`（指南已接入，内容持续补充中）
   - `Applied Energistics 2`
2. 可选依赖
   - `Just Enough Items`
   - `EMI`
   - `Jade` / `WTHIT` / `TOP` *使用 AE2 的 igtooltip 机制支持，不另外做兼容*
   - `GregTechCEu Modern`
3. 兼容模组
   - `Mekanism` *TODO*
   - `Create` *TODO*
4. 注入资源
   - `FTB Quests` *TODO*
5. 感谢
   - `Minecraft Forge`
   - `Fabric Loader`
   - `NeoForge`
   - `Applied Energistics 2`
   - `ExtendedAE`
   - `AdvancedAE`
   - `GregTechCEu Modern`
   - `BuildCraft`
   - `PackagedAuto`

</details>

<details>
  <summary><h3>库关系</h3></summary>

依赖名称：

- `kotlinx-coroutines-core`
- `compose-runtime`
- `compose-ui`
- `compose-foundation`
- `compose-foundation-layout`
- `compose-animation`
- `compose-material`
- `ojalgo`

包路径：

- `kotlinx.*`
- `androidx.*`
- `org.jetbrains.kotlin.*`
- `org.jetbrains.compose.*`
- `org.ojalgo.*`

</details>

## 辅助功能

- [x] `V` 键查看物品/方块详细信息

## 修改列表

### bug

修复破坏面板无频道也能工作的问题。

由于其机制确实不错，重新引入类似功能，允许不超过 64 个破坏面板与成型面板的组合结构，共用一个频道。

### 界面

逐步将 AE2 的界面替换为模组自己的实现，界面元素可以任意组合。

### 存储

- [ ] `移除` 创造 ME 物品元件、创造 ME 流体元件
- [x] `添加` 创造 ME 存储元件
- [x] `添加` 维度存储元件
- [ ] `添加` 所有存储元件的 $1 \text{Ki}$ 到 $256 \text{Ti}$ 版本

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

- [x] 当检测到 `GregTechCEu Modern` 模组已加载时，允许在能源元件上放置避雷针，一旦避雷针被雷击则下方的能源元件立即充满能量。

> 这是什么意思呢，我不知道啊 \[doge\]

### 终端

- [x] 新样板终端，将旧的编码模式修改为新的四种编码模式：
  - [x] 机器样板
  - [x] 处理样板
  - [x] 概率样板
  - [x] 伪样板

机器样板包括原版工作台、熔炉、切石机等配方，也包括模组的各种配方。

处理样板与原版一致。

概率样板就是处理样板，但是可以处理概率配方。

伪样板没有产物，只能发送不会回流。

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

`分子装配室` 可执行**配方类别**（`RecipeType`）而非某一具体机器方块。编码样板时选择类别（合成 / 熔炼 / 高炉 / 烟熏…）；装配室机器槽放入任意被该类别接受的物品即可。

机器物品匹配**仅服务端权威**（绝不依赖 JEI/EMI 客户端运行时），按 OR 组合：

1. **内置默认物品**（工作台、熔炉、高炉、烟熏炉）
2. **本模组 tag** `ae2isallyouneed:machines/<类别>`（整合包可追加）
3. **约定 tag**（其它 mod / loader 已填则自动生效）：如 `forge:furnaces`、`c:furnaces` 等

> 服务端即使安装了 JEI/EMI 也不会用它们做匹配：查看器 API 是客户端向的，不能作为权威来源。跨 mod 机器请进约定 tag 或本模组 tag。

- [x] 按 `RecipeType` 注册类别（crafting / smelting / blasting / smoking）
- [x] 默认物品 + 本模组 tag + 约定 tag（服务端）
- [x] 数据包扩展：`machines/types` + `machines/recipes`（手动优先 → recipe_source）
- [ ] JEI/EMI 一键填入编码终端（仅 UX，不参与匹配）
- [ ] 非 Container 配方适配器（GT 等）

#### 数据包格式 (Datapack Format)

- `data/<ns>/machines/types/` — 类别：`machines[]` + `tags[]`（OR），`auto_tag` 默认开（`id`→`ns:machines/<path>`）
- `data/<ns>/machines/recipes/` — 手动配方：`inputs[]` + `outputs[]`（输入 [BigIngredient]，输出 [BigStack]）

匹配顺序：**手动配方 → `recipe_source`**。

```jsonc
// types
{ "id": "ae2isallyouneed:example_custom", "machines": ["minecraft:lodestone"], "tags": [], "recipe_source": null }

// recipes — item 精确 / tag 通配
{ "machine_type": "minecraft:smelting", "inputs": [{ "item": "minecraft:cobblestone" }], "outputs": [{ "item": "minecraft:diamond", "count": 1 }] }
{ "machine_type": "minecraft:smelting", "inputs": [{ "tag": "minecraft:logs" }], "outputs": [{ "item": "minecraft:charcoal", "count": 2 }] }
```

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

多方块结构见 [异步合成多方块](docs/Async-Crafting-MultiBlock.md) 文档。

> [!WARNING]
> 不要自己搭建多方块结构，用 GT 的终端！

#### 异步合成网络

整个网络只能存在一个 `异步合成处理器`、多个 `异步合成网络交换机` 和多个 `异步合成模块`。`异步合成网络交换机` 扫描挂载在自身上的 `异步合成模块`；`异步合成处理器` 扫描直接或级联连接的 `异步合成网络交换机` 及其挂载的 `异步合成模块`。大多数代码内操作都由 `异步合成处理器` 承担，避免额外计算。

### ME 无线供能 (ME Wireless Power Transmission)

- [ ] 安装了 `无线供能升级` 的无线访问点可以自动向周围的接入设备供能，也可以选择为所有附近设备供能无论其是否接入此网络。

- [ ] 通过奇点连接的设备会自动受到 ME 网络的供能，并且尽可能快地供满电量，网络能量充足时 $1 \text{tick}$ 内直接充满。

### 机器人 (Robot)

[Robot](docs/Robot.md)

### IP / MAC 地址

每个 AE2 **网络节点**（`GridNode`）拥有一个 **48-bit 全局唯一 MAC**，用于稳定标识设备，供机器人远程绑定、网络图 / 调试元数据，以及基地搬迁后无需重绑无线设备。

| 项       | 说明                                                       |
|----------|------------------------------------------------------------|
| 格式     | 48-bit，显示为 `XX:XX:XX:XX:XX:XX`（本地管理位）           |
| 粒度     | 每个设备 `GridNode` 一个（多 Part、P2P outer 等各自独立）  |
| 覆盖     | 全部 AE2 **设备**节点；**线缆不分配 MAC**                  |
| 冲突     | 同一时刻一个 MAC 只对应一个存活节点；复制/冲突时自动重分配 |
| 分配     | 世界存档级注册表，首次入网时分配                           |
| 扳手拆除 | **保留** MAC，写入掉落物 NBT，重放后恢复                   |
| 普通挖掉 | **不保留**，再次放置分配新 MAC                             |
| 记忆卡   | 不复制 MAC                                                 |
| 与 AE2   | 不改变频道 / 路由 / 存储；纯附加身份层                     |

实现与 AE2 网络背景见 [`docs/AE2-Network.md`](docs/AE2-Network.md)。

- [x] 每节点 48-bit MAC 分配与世界持久化
- [x] 扳手保留 / 挖掉丢弃
- [x] 按 MAC 查找存活节点 API（`MacAddressRegistry`）
- [x] Jade / WTHIT / TOP 显示（AE2 igtooltip）+ 扳手物品 tooltip
- [ ] 机器人与远程控制绑定

## 版权

```text
===============================================================================
                             软件许可证协议
===============================================================================

版权所有 (c) 2026 copi143。保留所有权利。

本模组（包括源代码和编译后的二进制文件）为专有软件。
下载、使用或分发本软件即表示您同意以下条款和条件。

-------------------------------------------------------------------------------
1. 代码与编译后的二进制文件 (源代码及 Jar 文件)
-------------------------------------------------------------------------------

1.1 所有权
   除本协议明确授予的许可外，源代码及编译后的二进制文件的一切权利、所有权
   和权益均由原版权所有者保留。

1.2 使用与分发
   - 您可以自由使用、游玩本模组，并将其打包至任何整合包中（无论是私人使用
     还是公开分发），但必须满足以下条件：
     a) 编译后的二进制文件必须保持原样、未经修改地进行分发。
     b) 分发方式不得误导使用者，使其误以为该分发渠道为官方途径、官方发布
        或获得了官方授权。

1.3 源码分发与反编译
   - 严禁第三方公开分发本模组的原源代码或反编译后的源代码。

1.4 仓库 Fork 与贡献
   - 您仅出于向主项目提交贡献（如 Pull Request）的目的 Fork 本仓库。
   - 对于未使用 GitHub 原生 Fork 功能而直接手动上传创建的二次仓库，必须
     在显眼位置明确标明并指向源仓库。
   - 允许使用 Fork 仓库的 CI（持续集成）编译打包修改后的版本，但该编译
     产物仅可用于开发与测试用途，严禁公开分发修改后的二进制文件。

1.5 修改与扩展
   - 允许编写扩展模组（Addon）或在运行时进行修改（如通过 Mixin 或运行时
     补丁）。

1.6 非商业用途限制
   - 严禁将本模组的代码、二进制文件或任何衍生作品用于商业用途。包括但不
     限于出售本软件或将访问权限置于付费墙之后。

-------------------------------------------------------------------------------
2. 其它资产 (本地化文本与纹理资源)
-------------------------------------------------------------------------------

第 1 条中的限制仅适用于源代码及编译后的二进制文件。其它资产遵循以下独立
的许可协议：

2.1 本地化 / 语言文件
   所有本地化文本文件 均以 Creative Commons CC0 1.0 通用 (CC0 1.0) 许可发布。
   链接：https://creativecommons.org/publicdomain/zero/1.0/deed.zh

2.2 纹理与视觉资源
   所有图片纹理及非代码视觉艺术作品均以 Creative Commons 署名-非商业性
   使用-相同方式共享 4.0 国际 (CC BY-NC-SA 4.0) 许可发布。
   链接：https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh

===============================================================================
```
