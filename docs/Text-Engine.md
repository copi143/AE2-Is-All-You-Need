# 文本引擎与 Markdown 渲染(Minecraft 1.20.1 内嵌 Compose)

> 目标:为 minecraftx Compose 组件库引入**可切换的文本渲染引擎抽象**,并在此基础上
> 提供完整 GFM 的 Markdown 渲染组件;后续接入基于 MSDF 的矢量字体引擎。

---

## 1. 背景

- 本项目的 Compose 嫁接环境**没有 skiko**:文本完全绕过官方 text/paragraph 管线,
  由 `McText` 直接用 Minecraft 字体经 `GuiGraphics` 绘制。
- 第三方 Compose Markdown 库(mikepenz 等)依赖官方 Text/coil/skiko 管线,不可用。
- `McText` 仅支持单行截断,无自动换行,无法承载 Markdown 段落排版。

## 2. 分步路线图

### 第一步(已实施):引擎抽象 + Vanilla + Markdown

```
common/minecraftx/compose/                          # 实际 sourceSet: common/minecraftx (见 common/build.gradle.kts kotlin.srcDirs)
├── text/                              # ★ 文本引擎层(新增)
│   ├── McTextEngine.kt                # 接口:layout(McStyledString) → McTextLayout + DrawScope.paint
│   ├── McStyledString.kt              # IR:McStyledString(文本+非重叠 span)+ McSpanStyle + McSemantic
│   │                                  #   (自研轻量样式;Compose 1.12.0-beta03 的 SpanStyle 构造器
│   │                                  #   已 internal 化,无法跨模块构造,故弃用 AnnotatedString)
│   ├── McTextLayout.kt                # 不可变排版结果(行 → 带 McSpanStyle 的 run 列表),可缓存复用
│   ├── VanillaTextEngine.kt           # 默认实现:McStyledString→MC run 流,自研贪心折行器
│   │                                  #   (空格断行/CJK 逐字断行/超宽硬断,支持字间距变体),
│   │                                  #   paint 经 FormattedCharSequence→GuiGraphics 直绘
│   ├── LocalMcTextEngine.kt           # CompositionLocal 注入 + 引擎注册表 + rememberTextLayout 缓存
│   └── ComponentConverters.kt         # Component 树展平 → McStyledString(含 MC Style 继承解析;
│   │                                  #   以 contents.visit 而非 getString() 避免 siblings 重复拼接，已修复)
├── material/
│   ├── McText.kt                      # API 不变,内部改走 engine;新增 McWrappedText 多行组件
│   └── ...                            # 其余显示组件经 McText/McWrappedText 间接走 engine
├── markdown/
│   ├── MdBlocks.kt                    # MdParser:GFM AST → 块级 IR(MdBlock);内联样式展平为 span,
│   │                                  #   code/link 用语义角色(McSemantic),渲染时再解析主题色
│   └── McMarkdown.kt                  # GFM 渲染组件:标题/强调/删除线/行内码/链接/嵌套列表/
│                                      #   任务列表/引用/围栏+缩进代码块/GFM 表格/分隔线
└── theme/
    ├── McColorScheme.kt               # 新增 md* token(mdCodeBackground/mdCodeText/mdQuoteBar/
    │                                  #   mdRuleLine/mdHeadingAccent/mdLink)
    └── McThemeSettings.kt             # 新增 textEngineId 持久化开关(双层切换之全局层)
```

**关键设计**

| 决策 | 内容 |
|---|---|
| 中间表示 | 自研 `McStyledString` + `McSpanStyle`(含 `McSemantic.CODE/LINK` 语义角色):不泄漏 MC 类型,
  任何引擎都能解释;Compose 1.12.0-beta03 的 SpanStyle/TextDecoration 构造器已 internal 化,无法使用 |
| layout/paint 分离 | 对标官方 TextMeasurer:排版结果可缓存,表格两遍测宽/大文档重排友好 |
| 切换机制 | 双层:`CompositionLocalProvider(LocalMcTextEngine provides ...)` 局部覆盖 +
  `McThemeSettings.textEngineId` 全局开关(持久化到 client properties;demo 有 vanilla/spaced
  字间距变体实时对比);缓存 key 含 engine 实例 |
| 折行 | 自研贪心折行器:空格断点 + CJK 全字符断点 + 超宽硬断,单行模式等效旧 substrByWidth 截断 |
| 解析器 | `org.jetbrains:markdown-jvm:0.7.9`(Apache-2.0,零第三方依赖,GFM 表格/删除线/任务列表
  CHECK_BOX 内建;fabric include + forge jarJar 已随包分发) |
| Markdown v1 简化 | 表格对齐标记忽略、图片渲染为 alt 文本、链接有样式不可点击、输入组件光标定位未迁 |

### 第二步(已落地):`MsdfTextEngine` —— 系统字体 + 纯 JVM MSDF

> 现状:已接入。Demo 的引擎按钮可切 `vanilla` / `spaced` / `msdf`。

相对初稿的路线修正:

- **不引入 `lwjgl-msdfgen`**。MC 1.20.1 自带 LWJGL 3.3.1,再塞一套 3.3.4 natives
  会和 Forge/Fabric 的 native 解压、classloader 打架;kool 现用的也是预烘焙图集,不是运行时
  绑 msdfgen。轮廓距离场在 JVM 里直接算。
- **不 vendor kool 源码**。排版已有 `TextWrap`;shader 只翻译了 MsdfUiShader 的
  median3 / SDF+MSDF 按 pxRange 混合 / premultiplied alpha(~30 行 GLSL)。
- AWT 仍负责字体:family 探测、`canDisplay`、metrics、`getGlyphOutline` → 展平 PathIterator。

实现要点:

- **字体链**:按平台挑 Latin UI 字体 + CJK(YaHei / Noto Sans CJK / PingFang / 文泉驿…) +
  `SansSerif` 兜底;缺字按码点走下一面。
- **动态图集**:ASCII 后台预热;miss 进单 worker 算 MSDF;主线程每帧预算
  `glTexSubImage2D`;满则 CPU 缓冲翻倍(≤2048²)整张重传。本帧 miss 跳过,下帧渐现。
- **渲染**:自管 program / VAO / VBO,MVP = `RenderSystem` 投影 × (modelview × pose);
  绑 VAO 以免改掉 MC 字体的 VAO。bold 走 SDF weight,italic 走顶边 shear。
- **共享折行**:`TextWrap`(空格 / CJK / 硬断 / `\n`),Vanilla 与 MSDF 共用。
- **输入框**:`McTextField` / `McTextArea` 的测宽、点选、绘制都走 `McTextEngine`,
  不再写死 `Minecraft.font`。

两步仍通过 `McTextEngine` 解耦;`McText` / `McWrappedText` / `McMarkdown` 不用改调用方。

## 3. 风险备忘

| 风险 | 缓解 |
|---|---|
| StringSplitter 1.20.1 mojmap 签名差异 | 兜底自写逐字折行 |
| GFM 表格列宽启发式 | 两遍测宽按内容比例分配;极端输入可接受 |
| bold 字形加宽导致折行偏差 | 测量统一走带 Style 的宽度 API |
| AWT 轮廓 winding / 展平误差 | 用非零环绕数定号;曲线交给 AWT `PathIterator(flatness)` |
| atlas 扩容瞬间帧尖峰 | 后台生成 + 主线程预算上传;扩容只 memcpy CPU 缓冲再整张上传 |
| 自定义 GL pass 状态管理 | 自管 program/VAO,保存/恢复 blend/cull/texture/program(参考 McCanvas.fillTriangle) |
| MC 版本 | `gradle/libs.versions.toml` 锁定 `minecraft 1.20.1` / `forge 47.3.0` / `neoForm 1.20.1`，文档标题 1.20.1 为准 |
