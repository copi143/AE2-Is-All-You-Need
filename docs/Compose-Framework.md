# 自研 Compose 移植框架（Minecraft 1.20.1 / Forge）知识库

> 在 Minecraft 的 GUI 之上用 Compose Runtime（无 androidx.compose.ui）实现的布局框架。
> 本文记录已确认的运行时机制、崩溃修复、布局链路、当前放置 bug 的定位结论与测试方法。

---

## 1. 架构总览

```
common/src/main/kotlin/allyouneed/client/compose/
├── platform/
│   ├── ComposeScreen.kt        # 继承 Screen，把 render/mouse* 桥接到 ComposeOwner
│   └── ComposeOwner.kt         # 持有 Recomposer + LayoutNode 根 + 帧循环驱动
├── ui/
│   ├── node/
│   │   ├── LayoutNode.kt       # 布局核心：remeasure/modifier 链/place 链路/draw
│   │   └── UiApplier.kt        # AbstractApplier，把 ComposeNode 挂到 LayoutNode 树上
│   ├── layout/
│   │   ├── Layout.kt           # @Composable Layout()：ComposeNode + update
│   │   ├── Constraints.kt / MeasurePolicy.kt / Placeable.kt
│   │   └── Column.kt / Row.kt / Box.kt
│   ├── modifier/               # 自研 Modifier（无 androidx.compose.ui）
│   └── draw/McDrawScope.kt     # 基于 GuiGraphics 的绘制封装（pose 平移/矩形/文字）
├── material/                   # Text / Button / Slider / Spacer / TextField
└── demo/ComposeDemoScreen.kt   # 按 K 打开的测试页
```

渲染驱动链（每帧，游戏线程）：

```text
ComposeScreen.render
  → ComposeOwner.render
      → SnapshotSync.requestApply()        // 触发 recomposition 应用
      → rootNode.remeasure(constraints)    // 从根往下重新测量整棵树
      → .place(0, 0)                       // 从根开始放置，把 x/y 写回每个 LayoutNode
      → rootNode.draw(McDrawScope)         // 逐节点 translate(x,y) 绘制
```

---

## 2. 已确认的运行时事实（推翻过的假设）

1. **`Composition.performRecompose` 会替换 `measurePolicy`、`modifier`、`placeables`**；
   `Layout()` 的 `update` 块只在组合期执行，属性变化靠 recompose 时 `update` 块内的
   `set` 回调更新节点字段。因此只要 measurePolicy/modifier 每帧从节点字段读取，就是最新值。
2. **`remeasure` 时 latest snapshot 已应用**，modifier/measurePolicy 读到的是最新值。
3. **`Placeable.place` 是链式透传**：父节点调用它记录的 `placeAt(px, py)`，而
   LayoutModifier 的 Placeable 会先偏置内部 content Placeable（连同其子放置闭包）再调用。
4. demo 滚动容器内节点 `draw` 是被 `translate` 包裹的，绘制坐标是全局坐标，与放置逻辑无冲突。
5. **`Dispatchers.Main` 在 Minecraft 内没有 provider**（需要 swing/android/javafx），
   直接使用会抛 `IllegalStateException`（这是此前启动即崩溃的根因）。

---

## 3. 崩溃修复（已编译通过）

### 3.1 MinecraftDispatcher

用自定义 `CoroutineDispatcher` 把协程调度到 MC 游戏线程：

```kotlin
private object MinecraftDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !Minecraft.getInstance().isSameThread
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Minecraft.getInstance().execute(block)
    }
}
```

### 3.2 ImmediateFrameClock

Recomposer 用 `parentFrameClock.withFrameNanos(...)` 对齐工作。MC 没有帧回调，
改用「立即返回」的 `MonotonicFrameClock`，让 recompose+apply 在 `render()` 的
`SnapshotSync.requestApply()` 阶段内联完成：

```kotlin
private object ImmediateFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        onFrame(System.nanoTime())
}
```

### 3.3 其他

- `setContent` 加防重入保护（窗口 resize 会重复调 `init()`）。
- `render()` 从「只 `remeasure` 不 `place`」改为 `remeasure(...).place(0, 0)`，
  **否则所有 `LayoutNode.x/y` 永远为 0，全部内容会叠在左上角**（这正是放置 bug 的直接根源）。

---

## 4. 布局系统机制

### 4.1 测量：modifier 链包裹

`LayoutNode.remeasure(constraints)`：

```text
modifier.foldElements { LayoutModifier }
  → 从链尾向前逐层包装（padding/size/fillMax 收窄约束并偏置偏移）
  → 最外层 measurable.measure(原始 constraints)
  → width/height = 最外层 Placeable 的尺寸
```

例：`Modifier.fillMaxSize().padding(16)`，折叠序 = [FillMaxWidth, FillMaxHeight, Padding]，
反转后 Padding 最内、FillMaxWidth 最外，语义正确。

### 4.2 放置：Placeable 链

`remeasure` 返回的 Placeable：

```kotlin
Placeable(width, height) { px, py ->
    this.x = px
    this.y = py              // 记录节点自身全局坐标（draw 时 translate 用）
    outermost.placeAt(px, py) // 从最外层 modifier 开始透传
}
```

`MeasureResult.placeChildren(originX, originY)` 在 Column/Row/Box 内把子节点放在
**相对原点**的位置（例如 Column 的第 i 个子节点在 `(ox, oy + 前 i 个子高度之和)`）。

### 4.3 绘制

`draw()` 每层 `pushPose → translate(x,y)` 后画内容，叶子节点（Text/矩形）在
`(0,0)` 局部坐标绘制，由嵌套 translate 叠加成全局坐标。

---

## 5. 当前放置 bug 的状态与定位方法

### 5.1 症状

按 K 打开 demo 后，所有内容重叠在左上角（x/y 全部 ≈ 0），而非逐行排布。

### 5.2 静态分析结论

对照上述机制逐层推演：`place(0,0)` 后根→Column→子节点坐标应当被正确写入。
**纯逻辑层面未发现坐标错误**；若游戏内仍全部重叠，可能原因按优先级：

1. **运行的是旧构建**（修复未重新打包/未装进 mods 目录）——最高概率。
2. 组合期 `measurePolicy/modifier` 为空导致的测量退化（需运行时确认）。
3. 绘制阶段 GuiGraphics pose 叠加异常（理论上是 identity 每帧重建，低概率）。

### 5.3 定位手段（纯逻辑单元测试，不依赖 Minecraft/Forge）

common 测试源集已具备 JUnit5 + kotlin-test + compose.runtime，可手动构造
`LayoutNode` 树（用自定义 `MeasurePolicy` 替代 Text/Slider 等引用
`Minecraft.getInstance()` 的组件）：

- 根节点 `remeasure(Constraints(maxW,maxH)).place(0,0)` 后，断言 Column 子节点
  `x/y` 依次递增；
- 断言 modifier 链（padding/fillMaxSize/size）的约束收窄与偏移；
- 断言 `placeChildren` 原点传递正确。

### 5.4 测试结论（已验证，2026-08-07）

新增 `common/src/test/kotlin/allyouneed/compose/LayoutPlacementTest.kt`（10 个用例）
在 JDK17 下全绿：

- 根→Column(fillMaxSize().padding(16))：子节点从 (16,16) 起纵向排布 ✓
- Column/Row/Box 无 modifier 时子节点相对原点正确排布 ✓
- padding 偏移内容、size 夹紧尺寸、fillMaxSize 撑满约束 ✓
- 嵌套 padding 偏移叠加（10+5=15）✓
- 多次 remeasure/place 位置随最新调用更新 ✓

**结论：当前工作树的放置逻辑是正确的**（修测试辅助函数后全部通过；
之前 4 个用例失败是测试 helper 把父节点误当子节点引用，非产品代码 bug）。
因此「按 K 内容全部叠在左上角」的最可能原因是：

1. **游戏跑的是修复前的旧构建**（上一轮修改均未提交，jar 未重新打包/安装）——最高概率；
2. 运行时组合/快照时序问题（纯逻辑测试覆盖不到）。

### 5.5 真根因：UiApplier 重复插入（已修复，2026-08-07）

新增 `CompositionIntegrationTest`（真实 `Composition` + `UiApplier` + `Column/Row` 组合）
后确认根因：

**`UiApplier.insertTopDown` 与 `insertBottomUp` 都向 `parent.children` 插入了同一个节点。**

官方 `Applier` 契约（`androidx.compose.runtime.Applier.kt:71`）：
> *"An applier should insert the node into the tree either in insertTopDown or insertBottomUp,
> not both."*

且对**每个**插入的节点，composer 都会先调 `insertTopDown`（在该节点子节点插入前）再调
`insertBottomUp`（在其子节点插入后）。两个都改树 ⇒ 每个节点被插入两次：

```text
root.children = [Column, Column]   // 同一实例两次
Column.children = [Text, Text, ...]
```

连锁后果：

- 根节点 `measurePolicy=null` 且 `children.size==2`（≠1）→ `LayoutNode.remeasure`
  走 **else 分支：`Placeable(0,0){_,_->}`，不放置任何子节点**；
- 于是 Column 及其所有子节点 `x/y` 恒为 0；
- `draw()` 里每层 `translate(0,0)`，所有 Text/Button/Box 画在同一原点 →
  **全部内容重叠在左上角**。症状与现象完全吻合。

**修复**（`UiApplier.kt`）：

- `insertTopDown` 改为 no-op（树变更只发生在 `insertBottomUp`，对齐官方 bottom-up 构建，
  与 Android `NodeApplier` 一致）；
- `insertBottomUp` 负责 `children.add(index, instance)` + `instance.parent = current`；
- `remove`/`move` 改用官方 `AbstractApplier` 的 protected helper（修正了原 `move`
  前向移动索引 `to` 应减 `count` 的偏差）。

修复后：集成测试（root 恰 1 个 Column、子节点 padding 偏移/纵向排布正确）+ 全部
纯逻辑测试通过，`forge/fabric` 编译通过。**请重新打包 jar 并装进 mods 目录复测。**

> 历史教训：之前对 `remeasure/place`、modifier 链、placeChildren 原点的修复都是必要的，
> 但都建立在「树结构正确」的假设上；真正的结构性 bug 在 applier 层，纯逻辑单测（手工
> 建树）覆盖不到，必须用真实 `Composition` 的集成测试才能暴露。

建议：重新 `./gradlew :forge:build` 并把产物装进 mods 目录复测；若仍重叠，再
在 `ComposeOwner.render` 打印节点 x/y 定位运行时环节。

---

## 6. 官方 KMP Compose 复用评估

### 可选方向

| 方案 | 说明 | 风险 |
| --- | --- | --- |
| A. compose desktop (skiko) | 用官方 `androidx.compose.ui` 布局 + skiko 渲染到 MC GUI | 引入庞大的 skiko native 依赖，与 Forge 的 classloader/渲染线程冲突风险高；需提供自绘 canvas 宿主，MC 现有 `GuiGraphics` 无法直接喂给 Compose 的 `UIHierarchy` |
| B. 仅复用布局（去掉 rendering） | 官方 `LayoutNodeContainer`/`measure+placement` | 仍依赖 `androidx.compose.ui` 的 platform 抽象（PointerInput/density 等），割接成本接近重写 |
| C. 保持自研 | 继续修自研框架 | 可控，但失去官方语义保证 |

### 初步结论

官方 KMP Compose 的 UI 层与 Minecraft 的 `GuiGraphics` 渲染模型不兼容，强制接入
（A/B）的改造成本与风险远大于继续修复自研框架（C）。倾向 C，必要时按官方语义对齐
（例如把 Modifier 语义、`Constraints` 收窄规则逐条对齐官方实现）。

### 2026-08-07 复核（结合社区现状）

- 官方 `androidx.compose.ui` 的宿主模型是 **skiko `ComposeScene`**：由宿主每帧调用
  `performFrame → measureAndLayout → draw(canvas)`，绘制目标是 Skia `Canvas`，
  与 MC 的 `GuiGraphics`（Java 2D/内部 blit）完全异源。
- 社区移植方案（`compose-native-host`、`compose-desktop-native`、SkeletonGamer POC）都在
  **独立原生窗口**里跑官方 Compose + Metal/D3D/SDL 渲染，从未有人把官方 Compose 画进
  别的引擎的现有 GUI。要在 MC 里用官方 Compose，只能：离屏 Skia 渲染位图 → 每帧
  blit 进 MC 屏幕，外加 skiko 原生库打包（Forge classloader 与 native 加载冲突）、
  输入事件桥接、字体/物品渲染替换——工作量和风险远高于继续修自研框架。

**最终倾向（待放置 bug 实机复测后拍板）：C——保持自研，语义对齐官方。**
官方组件的 Modifier/Constraints/Placeable 语义可作为「参照实现」逐条对齐，
当前纯逻辑测试即为此用途。
