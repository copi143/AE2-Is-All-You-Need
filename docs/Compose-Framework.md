# Compose 框架（Minecraft 1.20.1 内嵌官方 androidx.compose.ui）

> 本框架把 **官方 `androidx.compose.ui` 的布局/测量/绘制引擎** 直接嵌入 Minecraft GUI：
> 不引入 skiko、不渲染离屏位图，而是把 Compose 的绘制指令桥接进 `GuiGraphics`。
> 早期"自研 LayoutNode/Modifier 引擎"方案已废弃并全部移除。

---

## 1. 架构总览

```
# 基础兼容层（保留在 allyouneed 下，仅依赖 MC + androidx.compose.ui）
common/src/main/kotlin/allyouneed/client/compose/
└── platform/
    ├── ComposeOwner.kt          # 官方 Owner 实现：Recomposer 帧时钟 + LayoutNode 根 + 输入桥接
    ├── ComposeLayer.kt          # ★ 嵌入面：任意 vanilla Screen 内嵌/全屏托管一个 Compose 子树
    ├── ComposeScreen.kt         # 全屏 Screen 薄适配器（内部持有一个全屏 ComposeLayer）
    ├── ComposeContainerScreen.kt# 容器型全屏屏（EMI 返回栈兼容）
    ├── ComposeApi.kt            # TooltipHost / MousePosition / FrameCallbackHost / Local* 共享 API
    ├── ScrollState.kt           # target/display 双值平滑滚动 + rememberScrollState（帧回调驱动）
    ├── McGraphics.kt            # 当前 GuiGraphics 的公开持有者（渲染桥接）
    ├── McCanvas.kt              # Canvas → GuiGraphics 指令桥（含 flush / scissor 透传）
    └── PassthroughLayer.kt      # 官方 OwnedLayer 的空透传实现（graphicsLayer 退化）

# 界面定义层（minecraftx.compose.*，全部基于基础兼容层构建）
common/src/main/kotlin/minecraftx/compose/
├── theme/
│   ├── McTheme.kt               # CompositionLocal 提供者 + McTheme.colors/typography/shapes 访问
│   ├── McColorScheme.kt         # 语义颜色契约（接口默认值 = 暗色主题）
│   ├── McTypography.kt / McShapes.kt
│   ├── DarkColorScheme.kt       # 默认暗色主题
│   └── LightColorScheme.kt      # 亮色主题（多主题控件 = 切换 McColorScheme）
├── material/                    # 主题化控件（读取 McTheme.colors，可单节点覆盖）
│   ├── McText.kt                # 统一文本组件（Component/String 重载 + 像素级 scissor 裁剪）
│   ├── McPanel.kt               # 带边框面板 + McCloseButton（colors 参数主题化）
│   ├── McScrollbar.kt / McTooltip.kt
│   └── ItemSlot.kt / EmiSlotRenderer.kt / VanillaSlotRenderer.kt
├── foundation/                  # 与主题无关的布局基元
│   ├── McVirtualColumn.kt       # 虚拟化滚动文本列（不可见行不组合）+ Modifier.mcScroll
│   └── McScrollBox.kt           # 通用 overflow 容器
├── geometry/
│   └── PanelGeometry.kt         # centeredRect / inset 纯函数
├── itemdetail/                  # item-details 屏幕（框架的真实使用示例，主题感知）
│   ├── ItemDetails.kt / ItemDetailsLayout.kt / ItemDetailsScreenFactory.kt
│   ├── ComposeItemDetailsScreen.kt   # ★ 完全由框架组件搭建的屏幕
│   └── ItemDetailsScreen.kt          # 遗留纯 vanilla 屏（保留不动，返回栈宿主）
└── demo/
    ├── ComposeDemoScreen.kt          # 按 K 打开：官方组件 + 框架组件混用 + 主题切换
    └── EmbeddedComposeDemoScreen.kt  # 按 L 打开：vanilla Screen 内嵌 ComposeLayer 面板
```

渲染驱动链（每帧，游戏线程）：

```text
ComposeLayer.render(g, mouseX, mouseY, partialTick, rect)
  → owner.render
      → frameClock.onNewFrame()                 // 唤醒 suspend 动画协程
      → frameCallbacks.advance()                // 滚动平滑等每帧步进
      → SnapshotSync.requestApply()             // 应用 recomposition 结果
      → updateRootConstraints(逻辑尺寸) + measureAndLayout()
      → dispatchMouseMove(px/uiScale - origin)  // 维护悬停状态
      → pose.translate(origin*scale); pose.scale(scale)
      → root.draw(McCanvas(graphics))           // 绘制指令桥接进 GuiGraphics
  → tooltipHost.render(g)                       // 浮动 tooltip 画在树之上
```

---

## 2. 坐标契约（核心约定）

所有几何统一用 **逻辑画布坐标** 表达，全链路换算如下：

| 概念 | 定义 |
| --- | --- |
| 逻辑 px | GUI 像素 ÷ uiScale（根 `Density(1f)`，因此 `1.dp == 1 逻辑px`） |
| 根约束 | 层矩形的逻辑尺寸（`rect.width/scale`） |
| 层内指针事件 | 层局部逻辑坐标：`(px/uiScale − origin)` |
| `mousePosition` / `positionInWindow()` | **全局逻辑**（层局部 + `origin`），悬停判定跨层一致 |
| 绘制 | `pose.translate(origin*scale)` 再 `scale(scale)`，节点逻辑坐标 → 屏幕 px |

- 全屏层（`origin=0`）：上述与旧行为逐字节等价。
- 内嵌层：宿主只给 `render` 传矩形 + 鼠标 raw px，其余全自动。
- scissor 裁剪（`McText` 半行可见时）：从 pose 矩阵读 `m30/m31/m00/m11`
  换算逻辑坐标 → 屏幕 px，调用 `GuiGraphics.enableScissor`。
- 鼠标经官方命中测试路由：滚轮事件只送到光标悬停的滚动节点，宿主屏**无需**自算
  "光标是否在面板内"。

---

## 3. 嵌入方式

### 3.1 全屏 Compose 屏（推荐入口）

```kotlin
class MyScreen : ComposeScreen(Component.literal("My")) {
    @Composable override fun Content() { McPanel(240.dp, 160.dp) { McText("hi") } }
}
Minecraft.getInstance().setScreen(MyScreen())
```

`ComposeScreen` 已处理：`setContent`（resize 防重入）、resize 重测、`render`、
鼠标三事件转发、Ctrl+滚轮整 UI 缩放（0.5x–4x）、`onClose` 释放。

### 3.2 内嵌子层（任意 vanilla Screen 内放一块 Compose 面板）

```kotlin
class MyScreen : Screen(...) {
    private val panel = ComposeLayer().apply { setContent { McPanel(240.dp, 160.dp) { ... } } }

    override fun render(g, mx, my, pt) {
        super.render(g, mx, my, pt)
        panel.render(g, mx, my, pt, Rect(left, top, left + w, top + h))   // rect 为逻辑坐标
    }
    override fun mouseClicked(mx, my, b) =
        panel.onMouseClicked(mx.toDouble(), my.toDouble(), b) || super.mouseClicked(mx, my, b)
    override fun mouseReleased(mx, my, b) =
        panel.onMouseReleased(mx.toDouble(), my.toDouble(), b) || super.mouseReleased(mx, my, b)
    override fun mouseScrolled(mx, my, d) =
        panel.onMouseScrolled(mx.toDouble(), my.toDouble(), d) || super.mouseScrolled(mx, my, d)
    override fun onClose() { panel.dispose(); super.onClose() }
}
```

要点：

- 一个层只 `setContent` 一次；`render` 每帧调用，矩形可动态变化。
- `Rect` 是 `(left, top, right, bottom)`——**不是** `(x, y, w, h)`；传 `(left, top, w, h)` 会造出
  负尺寸矩形，在缩放窗口后触发 item-details 类崩溃（防御性钳制见 `ComposeLayer.render`）。
- 未消费的输入回落到 `super`，与 vanilla 组件共存。
- `uiScale` 为 `mutableFloatStateOf`：在组合内读它订阅缩放，在事件处理器读它取值。
- 参考实现：`demo/EmbeddedComposeDemoScreen.kt`（按 L 键）。

---

## 4. 框架组件

| 组件 | 说明 |
| --- | --- |
| `McText(Component|String, color, modifier, maxWidth, clipFrame)` | 统一文本；`clipFrame` 给逻辑矩形 → scissor 像素裁剪（半行平滑滚动必需）；`color` 默认取 `McTheme.colors.textPrimary` |
| `McPanel(width, height, colors) { BoxScope }` | 固定尺寸带边框面板，背景/边框来自主题（`colors` 可单节点覆盖） |
| `McCloseButton(onClose, colors)` | ✕ 关闭按钮（默认 14×14），主题化 |
| `McVirtualColumn(lines, state, viewportW/H, lineHeight)` | 虚拟化文本列：不可见行不组合；容器带 `mcScroll` 滚轮；`McLine.color` 为 null 时取主题主色 |
| `Modifier.mcScroll(state)` | 滚轮 → `state.scrollBy(-dy*WHEEL_STEP)`，官方命中测试路由；消费增量以支持嵌套滚动 |
| `McScrollbar(state, trackColor, barColor)` | 点击跳转 + 拖拽 1:1（`seek`），颜色默认取自主题 |
| `rememberScrollState(maxScroll)` | 平滑滚动状态，自动向 FrameCallbackHost 注册 `advance` |
| `ItemSlot(stack, modifier, interactive, colors)` | 物品堆叠预览，EMI/vanilla 渲染器旁路，槽位配色来自主题 |
| `McScrollBox(contentW, contentH, modifier, scrollable, clip, autoScroll)` | 通用 overflow 容器：内容钳制/裁剪/滚动/滚动条四态，见 §4.1；滚动条配色来自主题 |
| `McTooltip(lines, modifier, ...)` | Compose 布局+渲染 tooltip（vanilla `renderMcTooltip` 的框架对偶版），见 §4.2；配色来自主题 |

滚动语义：`scrollBy`（滚轮）动 target、`seek`（拖拽）同时写 display/target 即时生效；
`display` 指数收敛到 target（`smoothingTime=0.06s`），静止时零写入。

### 4.1 `McScrollBox` —— 通用 overflow 容器

`McScrollBox` 持有一块 `contentWidth x contentHeight` 的虚拟内容区，视口尺寸取自调用方的 modifier
（`fillMaxSize()` 或 `size(w, h)`）。两种内容模型：

- **fixed（默认）**：显式传 `contentHeight`，子节点用绝对逻辑偏移相对内容原点摆放（虚拟画布）。
- **flow**：`contentHeight = null` 时内容以无限高测量（`Modifier.layout` 覆盖 maxHeight），
  测量出的内容高度自动回填到滚动范围——整页 `Column` 可整体滚动。K demo 的整页即此模式。

三种溢出策略由构造参数切换：

- **clamp（默认）**：内容原点钉在视口顶部并裁剪到视口内，超出的行不可见
  （等价 `Box` + `graphicsLayer(clip=true)`）。
- **scroll（默认）**：`scrollable=true` 时内容按共享 `state` 垂直滚动，滚轮由视口上的
  `mcScroll` 处理，滚动条 `McScrollbar` 绘在内容之上；`autoScroll` 在视口缩小（窗口缩放）
  时把偏移钳回 `[0, maxScroll]`。
- **ignore**：`clip=false` 时内容无条件溢出（尽量不用）。

裁剪用与 `McText.clipFrame` 相同的硬件 scissor：从 `GuiGraphics` 实时 modelview pose 推导
节点屏幕矩形，随缩放保持像素对齐；内容子节点因此**不应**再自带 `clipFrame`（scissor 区域
不支持嵌套）。参考：`demo/ComposeDemoScreen.kt` 的整页滚动与 McScrollBox 段、item-details 内容区。

`Modifier.mcScroll` 现在会**消费**滚轮增量，因此嵌套滚动容器（外层整页 McScrollBox 内的
McVirtualColumn / 内层 McScrollBox）不会一次滚两处：内层按叶子优先命中先处理并消费，外层
跳过已消费的增量。

### 4.2 Tooltip 双版本（vanilla 渲染 vs Compose 布局渲染）

同一份 tooltip 内容可以走两条完全不同的管线：

| | vanilla 渲染 | Compose 布局+渲染 |
| --- | --- | --- |
| API | `GuiGraphics.renderMcTooltip(font, tooltip, x, y)`（`platform/TooltipRenderer.kt`，复刻私有 `renderTooltipInternal`） | `McTooltip(lines, modifier)`（`material/McTooltip.kt`） |
| 布局 | 无：宽度/高度从 `ClientTooltipComponent` 累加得出 | `Column` + 若干 `McText`，按内容自动测量 |
| 放置 | 在树绘制后经 `TooltipHost` 浮动绘制，固定覆盖一切 | 作为普通 Compose 节点参与布局，可锚定任意节点或 `offset` 跟随鼠标 |
| 渲染 | `TooltipRenderUtil.renderTooltipBackground` + `ClientTooltipComponent.renderText/renderImage` | `McText`（MC 字体）+ 背景/边框 `drawRect` |

演示（K 键）：`ItemSlot` 悬停显示 **vanilla 浮动 tooltip**，右侧悬停框显示跟随鼠标的
**Compose 浮动 tooltip**（`McTooltip` 以 `pointerInput` Enter/Move/Exit 更新 `IntOffset`
状态，根层叠加定位）。

### 4.3 多主题控件

所有 `material/*` 控件的配色默认取自 `McTheme.colors`（一个 `McColorScheme`），因此换肤 =
切换 scheme，且可全局或局部生效：

```kotlin
// 全局：包住整屏内容（demo 的 K 屏用按钮在 Dark/Light 间切换）
var dark by remember { mutableStateOf(true) }
McTheme(colorScheme = if (dark) DarkColorScheme else LightColorScheme) {
    McPanel(width = 200.dp, height = 100.dp) { McText("Hello") }
}

// 局部：单个节点用别的配色
McPanel(width = 200.dp, height = 100.dp, colors = LightColorScheme) { ... }
```

- `McColorScheme` 是语义颜色契约（面板背景/边框、槽位、滚动条、tooltip、文本主色……），
  接口默认值即暗色主题；自定义主题只需 override 有差异的槽。
- 未包裹 `McTheme` 的组件回落到 `DarkColorScheme`，现有屏幕外观不变（向后兼容）。
- `McTheme` 用 `staticCompositionLocalOf`：切换 scheme 会整体重组合子树，全屏换肤即此语义。
- 颜色以 `Color`（ULong ARGB）承载；落进 `GuiGraphics.drawString` 的文本用
  `color.value.toInt()` 转回 `Int`（`0xAARRGGBB`，与 MC 一致）。

---

## 5. 运行时机制

1. **帧时钟**：`FrameClock` 实现 `MonotonicFrameClock`，`withFrameNanos` 挂起直到
   下一帧 `owner.render` 用 `onNewFrame()` 恢复——动画（`animate*AsState`、
   `AnimatedVisibility`）与游戏帧率同步，而非一次性完成。
2. **线程**：`MinecraftDispatcher`（`Dispatchers.Main` 在 MC 内无 provider）把协程
   调度到游戏线程；`Minecraft.getInstance().isSameThread` 判定。
3. **绘制桥**：`McCanvas` 把 Compose Canvas 指令转成 `GuiGraphics` 调用；
   所有绘制发生在 `pose.scale(scale)` 之内，因此整树自动跟随 Ctrl+滚轮缩放。
4. **graphicsLayer 退化**：`PassthroughLayer` 直绘，`graphicsLayer {}` 的绘制合成
   是空的（demo 用单个 Box 验证 alpha 动画场景可用）。
5. **tooltip**：`TooltipHost` 注册的浮动 tooltip 在树绘制后、pose 弹出后绘制，
   锚点按 `mouse * uiScale`（全局逻辑 → 屏幕 px），全屏/内嵌一致。
   默认走 **vanilla 渲染**（`renderMcTooltip`，EMI/ItemSlot 用）；需要参与 Compose 布局的
   版本用 `McTooltip`（§4.2），二者内容同源，管线互斥。
6. **suspend 文本输入**：`textInputSession` 抛错，Text 输入暂不支持。

---

## 6. 验证

- 单元测试（JVM，不依赖 Minecraft/Forge）：
  - `compose/platform/ScrollStateTest`：滚动收敛/钳制/即时 seek/静止零写入（注入时钟）。
  - `compose/spike/*`：官方 Owner 冒烟 + 指针悬停命中测试（`PointerHoverSpikeTest`）。
- 编译/打包：`:common:compileKotlin`、`:common:test`、`:fabric:build`、`:forge:jar`。
- 实机：K 打开 demo（官方按钮/滑条/动画 + 框架滚动面板 + McScrollBox 容器 + 双 tooltip；
  整页以 McScrollBox flow 模式滚动，内容超屏不再溢出），L 打开内嵌面板，
  V 打开 item-details 屏（Compose 渲染，滚动/拖拽滚动条/关闭按钮，小屏下面板收缩、内容区自动滚动）。

---

## 7. 设计决策记录

| 决策 | 理由 |
| --- | --- |
| 官方 `androidx.compose.ui` 而非自研引擎 | 早期自研引擎的 applier/测量链路反复出现结构性 bug；官方引擎有语义保证与完整测试 |
| 绘制桥接 `GuiGraphics` 而非 skiko 离屏 | skiko native 依赖与 Forge classloader 冲突、需要每帧 blit；桥接成本低且无渲染线程问题 |
| `ComposeLayer` 为唯一嵌入面 | 全屏与内嵌共用同一坐标/输入契约，宿主只需透传 render 与鼠标事件 |
| 返回栈留在遗留 vanilla `ItemDetailsScreen` | 重构以框架抽取为目标，不改动已验证的屏幕流转行为 |
| `Density(1f)`，1dp=1逻辑px | 让 item-details 原有逻辑像素常量（ItemDetailsLayout）可直接复用，杜绝双重缩放 |
| 裁剪统一走硬件 scissor（`drawClipped`/`McScrollBox.scissorClip`） | 从实时 pose 推导屏幕矩形，随缩放保持像素对齐；不依赖 graphicsLayer 离屏 |
| item-details 小屏自适应（BoxWithConstraints 钳制面板 + 动态重算 maxScroll） | 缩放窗口/小屏时面板收缩到可用尺寸而非被切掉，内容区高度变化后自动重新钳制滚动偏移 |
