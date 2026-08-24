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
common/src/main/kotlin/minecraftx/compose/
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
│   └── ComponentConverters.kt         # Component 树展平 → McStyledString(含 MC Style 继承解析)
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

### 第二步(规划中):`MsdfTextEngine` —— 无 stb 版 MSDF 矢量字体

调研结论(kool-engine/kool,Apache-2.0):

- kool 文本栈三层:**纯 Kotlin 排版测量层**(Font/MsdfFont/wrapText)、
  **MSDF 图集生成**(运行时从 TTF 生成,LWJGL 绑定)、**KSL 渲染 shader**(需翻译成 GLSL)。
- kool 用了**两套字体解析**(冗余):轮廓提取走 msdfgen 自带 freetype(`msdf_ft_*`),
  stb_truetype 只做 metrics/存在性检查。
- **去 stb 方案(已定)**:JVM 自带 AWT 全部接管 ——
  - `Font.createFonts()`:TTF/TTC/OTF 原生支持(TTC 集合免 offset 处理)
  - metrics:`GlyphVector.getGlyphMetrics()` / `getGlyphPixelBounds()` / `font.getLineMetrics()`
  - 存在性:`canDisplay(codePoint)`
  - 轮廓:`getGlyphOutline()` → PathIterator(SEG_MOVETO/LINETO/QUADTO/CUBIC 与
    TrueType 二次/CFF 三次曲线一一对应)→ msdfgen 低级 shape API 手工构造
    (`msdf_shape_create/add_contour/add_edge`);若绑定未暴露该 API,回落
    `msdf_ft_*`(freetype 随 lwjgl-msdfgen 自带,仍无 stb)
- **动态图集**(应对 CJK 大字符集):初始 atlas 预生成 ASCII → 运行时 miss 收集批量生成 →
  shelf packing 增量打包 + `glTexSubImage2D` 局部上传 → 满则翻倍扩容(≤2048²)+
  内存 LRU 缓存各字形 SDF 位图(扩容重打包免重算)。
- **渲染**:GLSL 翻译自 MsdfUiShader(~30 行核心:median3 三通道取中、SDF/MSDF 双通道
  按 pxRange 平滑混合、premultiplied alpha),MVP 接 MC pose matrix,与 McCanvas 直绘共用 context。
- **字体来源**:系统字体目录枚举(win/linux/mac)+ 常见中文字体自动探测
  (YaHei/Noto CJK/WenQuanYi/PingFang)+ 配置文件覆盖。
- **新增依赖**:仅 `org.lwjgl:lwjgl-msdfgen:3.3.x`(与 MC LWJGL 主版本对齐)+ 三平台 natives。
- **vendored 合规**:kool 子集源码(Apache-2.0)保留版权头。

两步通过 `McTextEngine` 接口完全解耦,第二步零改动已有组件代码。

## 3. 风险备忘

| 风险 | 缓解 |
|---|---|
| StringSplitter 1.20.1 mojmap 签名差异 | 兜底自写逐字折行 |
| GFM 表格列宽启发式 | 两遍测宽按内容比例分配;极端输入可接受 |
| bold 字形加宽导致折行偏差 | 测量统一走带 Style 的宽度 API |
| msdfgen shape 构造 API 暴露情况 | 第二步实施首日 spike 验证,缺失即走 freetype 回落 |
| atlas 扩容瞬间帧尖峰 | LRU 位图缓存 + 可选异步生成 |
| 自定义 GL pass 状态管理 | RenderSystem 状态保存/恢复模板 |
