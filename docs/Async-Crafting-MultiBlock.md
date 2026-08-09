# 异步合成多方块 (Async Crafting MultiBlock)

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

> `异步合成 ME 连接器`、`异步合成 WAN 连接器`、`异步合成 LAN 连接器` 注册为特殊的格雷仓室。

包含三个多方块结构：

- `异步合成处理器`
- `异步合成网络交换机`
- `异步合成模块`

## 详细定义

- `F` = `异步合成机器框架`
- `B` = `异步合成机器方块` / `异步合成机器玻璃` (底面不可以替换)
- `G` = `异步合成机器玻璃`
- `T` = `奇点合金加固空间塔`
- `E` = `异步合成能源核心`
- `S` = `异步合成存储核心`
- `P` = `异步合成计算核心` / `异步合成执行核心` (按照多方块类型选择)
- `C` = `异步网络控制器` / `异步网络交换机` / `异步合成工厂` (按照多方块类型选择)
- `Z` = `异步合成模块安装接口`

### 异步合成模块

$3 \text{宽} \times 7 \text{高} \times 5 \text{深}$

异步合成模块横向建造在 `异步合成处理器` 或 `异步合成网络交换机` 上：模块 $3 \times 7 \times 5$ 中深度 $5$ 的边对应结构 $19$ 宽的边，左右各放置一个；左侧模块的核心方块（`异步合成工厂`）朝左，右侧的朝右，方向错误不识别。

#### 正面 (一个 $3 \times 7$ 的矩形)

```text
FFF
FBF
FBF
FCF
FBF
FBF
FFF
```

#### 左/右侧面 (一个 $5 \times 7$ 的矩形)

```text
FFFFF
FBBBF
FBBBF
FBBBF
FBBBF
FBBBF
FFFFF
```

#### 侧面内部 (一个 $5 \times 7$ 的矩形)

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

### 异步合成网络交换机

$19 \text{宽} \times 7 \text{高} \times 11 \text{深}$ 深度按照重复数量增加

`核心结构` 中的 `异步合成机器方块` 可以替换为 `异步合成 WAN 连接器` 或 `异步合成 LAN 连接器`。

#### 正面 (一个 $13 \times 5$ 的矩形 `核心结构` + 下方两层大地板)

```text
FFFFFFFFFFFFF
FBBBBBBBBBBBF
FBBBBBCBBBBBF
FBBBBBBBBBBBF
FFFFFFFFFFFFF
大地板 (下面说明)
大地板 (下面说明)
```

#### 左/右侧面 (一个 $5 \times 5$ 的正方形 `核心结构` + 下方两层大地板)

```text
FFFFF
FBBBF
FBBBF
FBBBF
FFFFF
大地板 (下面说明)
大地板 (下面说明)
```

#### 侧面内部 (一个 $5 \times 5$ 的正方形 `核心结构` + 下方两层大地板)

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

#### 大地板 (一个 $17 \times 9$ 的矩形和一个 $19 \times 11$ 的矩形)

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

#### 可重复模块 (大地板也要扩大，每次扩大 $6$ 格)

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

### 异步合成处理器

$19 \text{宽} \times 15 \text{高} \times 19 \text{深}$ 深度按照重复数量增加

处理器上层地板为 $17 \times 17$，下层地板为 $19 \times 19$。$9 \times 9$ 内圈与 $5 \times 5$ 核心立方体之间是深度 1 格的 $7 \times 7$ 空气层，$5 \times 5$ 计算/存储核心立方体悬浮在空腔中。

#### 整体结构

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

#### 六个面 (一个 $13 \times 13$ 的正方形)

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

#### 内部一圈六个面 (一个 $11 \times 11$ 的正方形)

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

#### 再内部一圈六个面 (一个 $9 \times 9$ 的正方形)

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

#### 最内部

一个 $5 \times 5$ 的正方体，表面使用 `异步合成计算核心` 填充，内部使用 `异步合成存储核心` 填充。
