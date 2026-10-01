# Value Schema：值类型存储生成与应用计划

`sub/valueschema` 是一个 KSP 代码生成器：给满足 Valhalla 值类型契约的不可变 data class 生成高性能存储层。业务代码保持值语义写法（全 `val`、`copy` 变换），Valhalla GA 后删除生成层即零重构迁移。

## 契约

```kotlin
@ValueSchema(transforms = [
    ValueTransform(name = "cappedAt", params = "limit: Int", body = "if (price > limit) price = limit"),
])
data class Quote(val id: Long, val price: Int, val ts: Long)
```

- data class、主构造器全 `val`、字段为原始类型（Long/Int/Double/Float/Short/Byte/Boolean/Char）或另一个 `@ValueSchema` 类
- 嵌套值类型递归压平为叶子列（`price: Money` → `price_amount`/`price_scale`），与 Valhalla 扁平化语义一致；拒绝可空、泛型、递归嵌套（值类型无间接层）
- 不可依赖身份（`===`、`synchronized`、`identityHashCode`）

## 生成物

每个 schema 生成 4 个类型（`<Name>Schema.kt`）：

| 类型 | 说明 |
|---|---|
| `<Name>Columns` | SoA 列存：每叶子字段一个原始数组，列扫描/批量更新最快，可 SIMD |
| `<Name>Packed` | 位打包：单 `LongArray`，字段按位宽压入 64 位槽（贪心、不跨槽），整槽合并直接存储；内存密度最高 |
| `<Name>View` / `<Name>PackedView` | 零分配游标，热点路径用 `forEachView`/`view(i)` 避免物化 |

两种存储都是 `AbstractList<T> + RandomAccess`（只读 List 协议，元素访问会物化，走 List API 仅用于冷路径/互操作）。

零分配 API 分级：

1. **无条件**（不依赖 JIT）：原始参数 `add`/`set`（全叶子参数）、`@ValueTransform` 片段函数（字段读入局部变量 → 执行片段 → 整槽/逐列写回，kotlinc 编译有类型检查）、`view`/`forEachView`
2. **C2 标量替换**（热点稳态零分配）：`add(value)`、`updateAt`/`updateAll { it.copy(...) }`——实测与片段性能打平；解释期/megamorphic 失效，片段是保险

注意：`forEach` 一律用 `forEachView`——存储类实现 `Iterable` 后裸 `forEach` 会静默绑定到 `Iterable.forEach` 并物化每行。

## 已验证性能

JMH（`./gradlew :valueschema:jmh`，n=100,000，Quote = Long+Int+Long）：

| 场景 | ArrayList\<Quote\> | Columns | Packed |
|---|---|---|---|
| 扫描求和（读一列） | 99 µs | **25 µs（4×，SIMD）** | 56 µs |
| 全量更新（真变换） | 547 µs + 3.2MB 分配 | **21 µs，≈0 分配（26×）** | 118→111 µs，≈0 分配 |
| 追加 10 万行 | 272 µs | 416–428 µs | 591 µs |
| 随机物化单行 | 0.6 µs | 5.9 µs + 32KB | — |

结论：Columns 赢操作速度（单位步长可 SIMD）；Packed 赢内存密度（24B/行 vs AoS ≈44B/行），优势在超出缓存/内存带宽受限时才显现；值语义写法经 C2 标量替换后与片段打平。追加略慢（拷数据而非引用）与物化单行更贵是已知代价——物化只在边界做。

教训：benchmark 变换必须做真功（恒等变换被 C2 证明冗余后整个 DCE 掉）。

## 应用计划

### 一、自家代码（直接可用）

| 优先级 | 目标 | 现状 | 接入方式 |
|---|---|---|---|
| 1 | `me/pathing/ChannelGraph.kt`、`ChannelAllocator.kt`、`ChannelTreeAllocator.kt`、`ChannelMaxFlow.kt` | 手写 9+ 平行数组 + flags 位打包；每次寻路新建约 20 个临时数组 | 节点/边记录声明为 `@ValueSchema`（node: maxChannels/flags/group/connA/connB；edge: to/cap/rev/next），生成列存并复用实例，消掉每轮新建数组。纯重构不碰依赖，也是生成器在真实图算法（CSR 邻接 + 位打包 flags）下表达力的验证 |
| 2 | `parts/logger/NetworkLogEntry.kt` | 4096 条/台环形缓冲、按 `kind.category.mask` 全量过滤、64 条/页同步 | `kind` 改 Int（网络层已是 ordinal）、`args` 字符串驻留化为 Int id → `(utcMillis: Long, kind: Int, argsId: Int)` 三列，过滤变纯 IntArray 位运算扫描 |
| 3 | `util/bigint/ObjectCounter.kt` 值列 | 手写 `LongArray lo/hi` 平行数组 | U128 值 `(lo: Long, hi: Long)` 作嵌套值类型 |
| 4 | `parts/planebus/PlaneClusterMath.kt` 的 `PlaneClusterMember` | `(pos: Long, isBus: Boolean, isForming: Boolean)` 已全原始位打包 | 定义即适用，但规模 ≤96 条，收益在替代手写而非性能 |

### 二、AE2 依赖内部（mixin `@Redirect` 构造点 / 整体替换）

| 优先级 | 目标 | 现状 | 替代方案 |
|---|---|---|---|
| 1 | `appeng.me.pathfinding.PathingCalculation` | 每次寻路新建 3×ArrayDeque + 3×HashSet + Reference2IntMap，大网数万垃圾 | 整体替换（单构造点，无 public API 泄漏，风险最低）：GridNode 编稠密 id → visited 变 BitSet、bottleneck 变 `int[]`。与 `PathingCalculationMixin` 已有的 compute 替换衔接 |
| 2 | `appeng.api.stacks.KeyCounter`/`VariantCounter` | 两级 fastutil 哈希；合成模拟/CPU tick/存储全用它，每 tick 新建多个 | 配合 KeyInterner 已完成的单例化，AEKey 编稠密 int id → `(keyId: Int, amount: Long)` 平行数组；fuzzy 子索引用排序平行数组替 AVL 树。触及 public API，需兼容策略 |
| 3 | `appeng.me.service.TickManagerService` | `PriorityQueue<TickTracker>` 包装对象 × 数千节点 | `(nextTick: Long, rate: Int, nodeId: Int)` SoA + int[] 二元堆 |
| 4 | `appeng.crafting.CraftingTreeNode` | 每次模拟重建树 + LinkedHashMap，CRAFT_LESS 二分重跑 | 节点 SoA + children CSR 偏移数组，改动面大排后 |

### 三、EMI 依赖内部

| 优先级 | 目标 | 现状 | 替代方案 |
|---|---|---|---|
| 1 | `dev.emi.emi.registry.EmiRecipes.Manager` | `byInput/byOutput` 每 key 一个 `EmiStack.copy()` + LinkedHashSet，常驻数十万条目 | bake 是离线阶段，在完成点 mixin 后处理：stack/recipe 编 int id → CSR 索引（offsets[] + recipeIds[]），内存降 3–5×，查询从 NBT 递归 hash 变 int 比较。`byWorkstation` 是 public 字段，风险中等 |
| 2 | `dev.emi.emi.bom.TreeCost` | 每次背包变化 `clear()` 全量重建 4 张 HashMap + 数千小对象 | 树扁平化平行数组，recalculate 变清零数组；包自闭合适合整体替换。`MaterialNode` 字段被 BoMScreen 直读，先不动 |

### 四、其他

- 原版 `CompoundTagMixin`（≤4 条目槽位字段替 HashMap）与 GTCEu `QuadVerticesInterner`（int[32] 去重）是手工紧凑存储的存量证据，非迁移目标
- `sub/indexing` 的 FM-index（SuffixArray @Overwrite）同为"整体替换算法"路线先例

## 前提与风险

- 依赖侧共同前提：**对象 → 稠密 int id**（KeyInterner 已完成 AEKey 单例化这半步）
- 生成器当前只覆盖扁平记录；图结构（CSR）、字符串驻留表需手写或与生成层组合——`me/pathing` 迁移会先暴露这些缺口
- 生成层与 mixin 正交互补：生成器负责"替代后的结构"，mixin 负责"换进去"
- AE2/EMI 侧改动跟随依赖版本升级需复查（参照 GTCEu mixin pin 7.5.3 的先例）

## 执行顺序

1. ~~`me/pathing` 手写 SoA → 生成 SoA~~（完成）
2. ~~`NetworkLogEntry` 列存化~~（完成，`NetworkLogRow` 含 enum 字段）
3. ~~AE2 `PathingCalculation` 整体 SoA 重写~~（已由既有 mixin 整体替换，失效）
4. AE2 `TickManagerService`：`PriorityQueue<TickTracker>` → `me/tick/IndexedHeap`（mixin 持有堆下标，remove/重定位 O(n)→O(log n)，排序不变；完成，堆本体 262 项测试中 6 项覆盖）
5. AE2 `KeyCounter` int-id 化（需兼容策略设计；注意：KeyInterner 单例化前提尚未存在，fuzzy AVL 顺序需精确复刻）
6. ~~EMI bake 后 CSR 化~~（完成：`EmiRecipes$Manager` 的 byInput/byOutput 在构造 RETURN 注入点拍平为 `util/CsrIndex`（offsets[]+recipeIds[]），stack→intId 保留原 ComparisonHashStrategy 映射，原 map 字段置空释放；查询返回不可变随机访问视图，顺序语义不变；`byWorkstation` public 字段未动）

参考源码：AE2 15.4.10 / EMI 1.1.24 sources 解包于 `.tmp/ae2src`、`.tmp/emisrc`。
