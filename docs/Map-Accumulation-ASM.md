# 通用 Map 累加 ASM 原型

2026-10-05，基于 `3dc6903` 的本地实验实现，MC 1.20.1 / Java 17。

## 状态

已实现跨模组的调用点规则、方法内值来源分析、运行时守卫及原调用回退。
默认关闭，使用 `-Dallyouneed.mapaccumulation=true` 启用。

这是实验原型：匹配能力已经在真实模组中验证，但当前保守守卫的覆盖率不足，
没有测到稳定的整体重载收益，因此不作为默认性能优化。

## 匹配与改写

规则不包含 GTCEu、Botania 或 Mekanism 的类名。它匹配以下调用：

```java
Object2IntMap.mergeInt(key, delta, Integer::sum)
Object2IntOpenHashMap.mergeInt(key, delta, Integer::sum)
```

支持 fastutil `IntBinaryOperator`、JDK `IntBinaryOperator` 和 `BiFunction` 三种重载。
通过 ASM 分析回调的生产指令，沿局部变量、复制、CHECKCAST 和控制流合流追踪来源。
所有可能来源必须是标准 LambdaMetafactory 构造的、无捕获的 `Integer::sum`。
参数、字段、未知工厂方法、null、自定义 lambda、捕获变量及其他运算不会被猜测为加法。
未知来源与已知来源合流后仍为未知。

参数按原顺序求值一次，保留原来的 invokedynamic，然后执行：

1. 接收者的实际类必须恰好为 `Object2IntOpenHashMap`，子类和其他 Map 实现回退。
2. 键必须是 null、受支持的 JDK 最终值类型、Enum，或者实际继承 Object 的 equals/hashCode。
3. 满足条件时调用 fastutil 8.5.9 的具体 `merge(key, delta, Integer::sum)`：一次 find、返回新值。
4. 不满足条件时执行原 opcode、owner、descriptor 的调用。

没有直接使用 `addTo`：8.5.9 的 `addTo` 会调用 storedKey.equals(queryKey)，
原路径使用 queryKey.equals(storedKey)；此外它返回旧值，缺失键时还受默认返回值影响。
所选的具体 `merge` 保留比较方向、非零默认值行为及新值返回语义。
对未知键保留原调用，可以保留其 hashCode/equals 的调用次数和可能抛出的异常。

键方法判断使用 ClassValue 缓存，不会全局强引用模组 ClassLoader。
该规则仍遵循 fastutil 非线程安全 Map 的使用契约，不为并发写入增加线程安全保证。

## 分析边界

- 仅方法内值来源分析，尚未实现完整 SSA、跨方法效果摘要或逃逸分析。
- 方法预算：最多 4096 个指令节点，最多 256 个局部变量槽和最大栈槽；来源集合超过 8 个时退化为未知。
- 构造器、类初始化器、接口及 fastutil 自身类不处理。
- 分析失败或不满足规则时保留原字节码；运行时 API/注入失败时关闭该 pass。
- 通过识别已插入的守卫调用实现幂等，不添加调用方字段或方法，不改变其反射成员结构。
- 目标库是 MC 1.20.1 的 fastutil 8.5.9。注入代码只编译依赖它，模组不额外打包 fastutil。

## 正确性验证

回归测试覆盖三种函数重载、局部别名、已知/未知来源合流、非零默认值、null 键、整数溢出、
子类方法重写、其他 Map 实现、参数求值顺序、调用点前已有 long 栈值、原异常处理器、
自定义键的副作用和异常、碰撞时的 equals 方向、分析预算及重复变换。

测试直接读取配置中 GTCEu 7.5.3 的真实 `StagingRecipeDB.class`，确认两处调用被改写，
并在独立服务器中实际加载和执行改写后的类。Fabric 与 Forge 均完成启动、三次重载、
状态请求和正常退出。

## 实际识别范围与守卫命中率

Forge 诊断运行自动识别了五个类中的七个调用点：

| 类 | 调用点数 |
| --- | ---: |
| GTCEu `StagingRecipeDB` | 2 |
| GTCEu `SimplePredicate` | 2 |
| Botania `CorporeaHelperImpl` | 1 |
| Mekanism `TileEntityDigitalMiner` | 1 |
| Mekanism `TileEntityFormulaicAssemblicator` | 1 |

加载时识别不表示所有机器逻辑均被执行。空白测试世界未模拟矿机、合成器和 Corporea 运行。

独立诊断开关 `-Dallyouneed.mapaccumulation.stats=true` 会在退出时输出守卫统计；
正常模式不进行逐调用计数。一次启动加三次重载得到：

| 键类型 | 快速路径 | 回退 |
| --- | ---: | ---: |
| `IntCircuitIngredient` | 19,824 | 0 |
| 原版 `Ingredient` | 8,640 | 0 |
| `Integer` | 112 | 0 |
| `SizedIngredient` | 0 | 135,460 |
| `EnergyStack` | 0 | 123,456 |
| `FluidIngredient` | 0 | 40,992 |

快速路径占约 **8.70%**。后三类重写了键方法，当前分析无法证明它们的语义条件，故保守回退。
诊断计数只用于覆盖率分析，计时对照中关闭。

## JMH 微基准

Java 17；2 个 fork；2×500ms 预热、3×500ms 测量；每次新建 Map 累加 16,384 次。
下面单位为每次累加的 ns，已经包含按操作数摊销的 Map 创建和扩容成本。
± 为 JMH 的 99.9% 置信区间半宽。`custom` 键重写 hashCode，用于测量回退开销。

| 不同键数 | 键 | 原版 | ASM |
| --- | --- | ---: | ---: |
| 256 | 身份键 | 3.653 ± 0.207 | 2.985 ± 0.330 |
| 256 | 字符串 | 5.869 ± 0.107 | 3.270 ± 2.779 |
| 256 | 自定义键（回退） | 2.516 ± 0.065 | 3.723 ± 0.426 |
| 4096 | 身份键 | 6.018 ± 1.256 | 7.586 ± 0.113 |
| 4096 | 字符串 | 18.076 ± 0.243 | 9.459 ± 0.312 |
| 4096 | 自定义键（回退） | 4.033 ± 0.057 | 5.094 ± 0.044 |

4096 字符串键样本约快 1.91 倍；身份键不稳定，自定义键必然多承担守卫成本。
两组分配量基本相同：256 键约 0.503 B/update，4096 键约 8.015 B/update。

运行默认较长基准：

```bash
./gradlew :transformer:benchmark --offline --args='perf.MapAccumulationBenchmark -prof gc -foe true -rf json -rff map-accumulation.json'
```

原始短基准文件为 `.tmp/map-accumulation-256.json` 和 `.tmp/map-accumulation-4096.json`。

## Forge 重载对照

复用前一轮独立存档，每个条件三个 JVM，ON/OFF、OFF/ON、ON/OFF 交替。
保持 Gson POJO 快速路径关闭、Component 路径开启，仅切换 Map pass。
诊断计数关闭，JFR 设置和命令流程相同；验证输入快照与 transformer 插件哈希一致。

| 阶段 | OFF 中位数（范围） | ON 中位数（范围） |
| --- | ---: | ---: |
| 启动至 Done | 18.812 s（18.260～18.956） | 18.310 s（18.309～18.655） |
| 三次重载合计 | 6.782 s（6.760～7.146） | 6.840 s（6.738～6.986） |
| 三次重载 JVM CPU 时间 | 29.84 s（29.25～36.27） | 31.19 s（30.31～32.69） |

配对变化方向不一致。重载中位数约增加 0.84%，当前样本没有证明稳定净收益。
三组数据也不足以将约半秒的启动中位数差异归因于本 pass。
两组都完成重载、返回合法状态 JSON 并正常退出，配方/进度加载日志一致；
原有 Forge 侧别和 loot 表错误在两组仍各出现相同的 8 条。

原始数据、JFR、日志和脚本保留在 `.tmp/map-accumulation-ab`，汇总为 `analysis.json`。

## 后续判断

通用匹配框架已得到实际验证，但不能把“改写了调用点”等同于“实际更快”。
当前版本保持默认关闭，适用于继续实验和发现其他具有明确语义的优化模式。
若继续这条规则，应先研究能够可靠证明键方法效果的摘要，或避免在高回退率站点保留守卫；
不能仅删除键守卫以扩大覆盖率。完整的跨方法/逃逸分析应作为后续独立工作评估。
