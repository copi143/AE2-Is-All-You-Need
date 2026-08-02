# AE2 Is All You Need

- $5$ 个不同大小的元件实在太少了，加到 $20$ 个！从 $1 \text{Ki}$ 到 $256 \text{Ti}$ 全覆盖。
- 原版 AE2 只能显示 $9.2 \text{E}$ 的物品或 $9.2 \text{P}$ 的流体，后期终端内大量超过显示上限的物品，这个模组移除了显示上限，让你能看到你的网络到底存储了多少物品。
- 大型科技包的完结任务物品合成起来真的太慢了，这个模组提供了 $1 \text{tick}$ 合成任何物品的方案，让你能够批发任何物品。

> 提及容量数字时请注意 [SI 词头](https://zh.wikipedia.org/wiki/%E5%9B%BD%E9%99%85%E5%8D%95%E4%BD%8D%E5%88%B6%E8%AF%8D%E5%A4%B4) 与 [IEC 词头](https://zh.wikipedia.org/wiki/%E4%BA%8C%E9%80%B2%E4%BD%8D%E5%89%8D%E7%BD%AE%E8%A9%9E) 的区别。

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

### 材料

- [ ] `奇点合金`、`奇点合金块`：一种高级材料。

#### 物质球、奇点

做起来太简单的奇点肯定是不行的。

原版的 `物质球` 改为 `物质球 T2`，`奇点` 改为 `奇点 T0`。

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

## 新增概念

### 机器样板 (Machine Pattern)

`分子装配室` 经过了大幅升级，已经不只能处理工作台配方了，其可以处理任何注册的机器的配方，但需要将机器塞入分子装配室的机器槽位中。

### 异步合成 (Async Crafting)

将合成任务委托给 `异步合成处理器`，它会控制 `异步合成模块` 在完成合成，任意复杂的合成树执行时间均为 $1 \text{tick}$，合成速度只受限于 AE 本身计算合成树的速度。

三个相关多方块结构：

> 此处同时介绍“游戏设定”与“代码实现”，注意区分其不同之处。

- `异步合成处理器`：控制异步合成模块的工作，接收合成请求，计算合成树，分配任务。
- `异步合成网络交换机`：扩展异步合成处理器的网络，允许大量异步合成模块协同工作。
- `异步合成模块`：存放样板并执行合成任务，输入输出全部在 `异步合成网络` 中完成，不支持物品输入输出；实际的代码中合成任务由 `异步合成处理器` 进行处理，`异步合成模块` 只负责提供样板数据。

包含以下方块：

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

#### 异步合成网络

整个网络只能存在一个 `异步合成处理器`、多个 `异步合成网络交换机` 和多个 `异步合成模块`。`异步合成网络交换机` 扫描挂载在自身上的 `异步合成模块`；`异步合成处理器` 扫描直接或级联连接的 `异步合成网络交换机` 及其挂载的 `异步合成模块`。大多数代码内操作都由 `异步合成处理器` 承担，避免额外计算。

#### 多方块结构

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

处理器上层地板为 $17 \times 17$，下层地板为 $19 \times 19$。`9 \times 9` 内圈与 `5 \times 5` 核心立方体之间是深度 1 格的 `7 \times 7` 空气层，`5 \times 5` 计算/存储核心立方体悬浮在空腔中。
