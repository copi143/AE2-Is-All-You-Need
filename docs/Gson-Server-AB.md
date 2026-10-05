# Gson POJO 快速路径：服务器 A/B 对照

测量日期：2026-10-04。代码基线：`3dc6903d0a19c450d17a06de31a4e79f1c26f3b6`，
即变基到 `1.20.1` 的 `e99c0bf` 后的 Gson 分支。

## 结论

当前开发服务器的启动、重载、状态请求及固定区块操作，**没有给出足够证据证明应当默认全局启用 POJO 快速路径**。
重载中确有 Gson 热点，但主要位于 JSON 树解析、树查询、自定义序列化器及配方条件处理；
这些路径不由生成的 POJO 适配器接管。

建议将 POJO 路径保留为可选实验功能，暂缓扩大其覆盖范围。
下一步更有依据的研究目标是 `SimpleJsonResourceReloadListener` 的 JSON 树读取、
`CraftingHelper.processConditions` 的树查询和 loot 自定义反序列化中的临时对象。
若目标是更大幅度的整体重载收益，还应结合文末完整调用栈分析，优先考虑方块形状缓存和 GTCEu 配方构建。
本轮只做测量与记录，没有更改默认开关或运行时代码。

## 方法

- Minecraft 1.20.1；Temurin 17.0.20.1；`-Xms1g -Xmx2g`。
- Linux，AMD Ryzen 9 9950X（16 核、32 线程）。
- Fabric Loader 0.19.3；Forge 47.3.0。采用各自的当前开发运行依赖，两个 loader 的模组集合不同，
  所以只比较同一个 loader 内的开关结果。
- 导出 Gradle `runServer` 的实际 JVM 配置、classpath、参数和额外环境变量，再直接启动游戏 JVM。
  计时排除了 Gradle 配置、编译和依赖准备。
- 切换 `-Dallyouneed.gsonfast=true/false`，始终设置 `-Dallyouneed.componentjson=true`。
  两组都保留相同模组及钩子安装，测量的是生成适配器开关的增量影响。
- `.tmp/gson-server-ab` 内使用独立平坦世界，种子 `73419281`，视距与模拟距离均为 4，
  关闭生物生成、昼夜/天气变化和随机刻，固定加载一个区块。
- 每端先进行一次成功的种子存档启动、状态请求、初始化与保存，再从该快照复制每个正式运行目录。
  六次正式运行的完整输入 SHA-256 在每端内一致。
- 每端三组配对，顺序为 `ON/OFF`、`OFF/ON`、`ON/OFF`，共 12 次独立 JVM。
  每组先运行 Fabric 配对，再运行 Forge 配对，所有游戏 JVM 串行运行。
- 每次运行依次执行：启动至 `Done`、等待状态就绪、静置 2 秒、三次 `/reload`（间隔 0.5 秒）、
  200 次协议 763 状态请求、10 次对同一 16×16 平面的 stone/glass 替换、`save-all flush`、诊断及正常退出。
- 操作通过本机 RCON 同步执行。已检查 1.20.1 的 `MinecraftServer.reloadResources`：
  在服务端线程上通过 `managedBlock(completablefuture::isDone)` 等待重载完成，
  所以这里记录的是命令完成时间，而非仅发送 `Reloading!` 的时间。
- CPU 时间取 `/proc/<pid>/stat` 中 JVM 全进程的 user + system 累计值；多线程下可大于墙钟时间。

正式运行全部退出成功。配方/进度加载日志在开关两组中一致。
初次 Forge 种子运行曾在 `Done` 后立即获取到尚未填充版本信息的状态响应；
检查改为等待状态就绪后通过。该次失败的预备运行不计入正式结果。

## 耗时结果

下表均为每种条件三次运行的中位数，括号为最小值～最大值。
重载一行表示同一个 JVM 内三次重载的总时间。

| Loader | 阶段 | OFF | ON | ON 相对 OFF 中位数变化 |
| --- | --- | ---: | ---: | ---: |
| Fabric | JVM 启动至 Done | 6.728 s（6.628～6.728） | 6.779 s（6.626～6.879） | +0.76% |
| Fabric | 三次重载合计 | 0.866 s（0.844～0.866） | 0.879 s（0.835～0.888） | +1.57% |
| Forge | JVM 启动至 Done | 19.509 s（19.320～19.615） | 19.215 s（19.119～19.365） | −1.51% |
| Forge | 三次重载合计 | 7.170 s（7.169～7.486） | 7.219 s（7.141～7.313） | +0.68% |

启动的变化方向在两个 loader 间不一致，重载配对的变化方向也不一致。
三组配对不足以确认约 1% 的启动差异是稳定收益。

| Loader | 阶段 | OFF 中位数 | ON 中位数 |
| --- | --- | ---: | ---: |
| Fabric | 200 次状态请求 | 123.9 ms | 117.0 ms |
| Fabric | 强制存档 | 80.0 ms | 80.2 ms |
| Forge | 200 次状态请求 | 122.9 ms | 115.2 ms |
| Forge | 强制存档 | 114.5 ms | 117.5 ms |

状态请求包含本机连接及协议处理，持续时间很短，且 ServerStatus 使用 Codec 路径。
未发现足够 POJO 热点证据将其差异归因于本优化。

| Loader | JVM CPU 时间 | OFF 中位数 | ON 中位数 |
| --- | --- | ---: | ---: |
| Fabric | 启动 | 38.93 s | 39.49 s |
| Fabric | 三次重载合计 | 4.14 s | 4.16 s |
| Forge | 启动 | 107.51 s | 106.45 s |
| Forge | 三次重载合计 | 33.51 s | 35.46 s |

## 分配与调用栈

两组均启用 JFR profile：Java 执行采样周期 10 ms、分配采样节流 300/s、栈深 128、
开启 ClassDefine，按控制脚本记录的时间范围切分启动和各次重载。

以下分配量是 `jdk.ObjectAllocationSample.weight` 的累计估计，单位 MiB，
**不是存活堆大小，也不是逐对象精确计数**。短阶段和少数调用栈的估计可能受采样影响明显。

| Loader | 阶段 | OFF 中位数 | ON 中位数 |
| --- | --- | ---: | ---: |
| Fabric | 启动 | 4,060 | 3,990 |
| Fabric | 三次重载合计 | 1,268 | 1,349 |
| Forge | 启动 | 21,731 | 22,005 |
| Forge | 三次重载合计 | 14,843 | 14,223 |

这组分配估计没有呈现跨 loader、跨阶段一致下降，不能据此声称 POJO 路径减少了整体分配。

三次正式运行汇总的重载 Java 执行样本：

| Loader | 开关 | 所有 Java 样本 | 含 Gson 栈帧的样本 | 含反射/生成 POJO 适配器栈帧的样本 |
| --- | --- | ---: | ---: | ---: |
| Fabric | OFF | 105 | 4 | 0 |
| Fabric | ON | 93 | 5 | 0 |
| Forge | OFF | 1,219 | 126 | 0 |
| Forge | ON | 1,102 | 120 | 0 |

零样本并不证明从未调用，只说明这些适配器没有成为本次采样可见的重载热点。
Forge 中约十分之一的 Java 样本含 Gson 帧，主要代表以下调用链（按调用方向列出）：

```text
SimpleJsonResourceReloadListener.scanDirectory
  → GsonHelper.fromJson
  → TypeAdapters$28.read
  → JsonReader.nextQuotedValue / nextName / nextString

RecipeManager.apply
  → CraftingHelper.processConditions
  → JsonObject.has
  → LinkedTreeMap.find

LootTable / LootPool 的自定义 Serializer
  → TreeTypeAdapter.read / Gson.fromJson
  → JsonTreeReader
```

对应的分配样本包含 `JsonReader` 及其数组、`JsonTreeReader`、`LinkedTreeMap.Node` 和集合扩容。
这些工作属于树构建、树遍历和自定义适配器；当前 POJO 生成器复用反射字段绑定的机制不覆盖它们。

## 实际生成的适配器

在所有操作完成后、停止服务器前，用 `jcmd VM.class_hierarchy` 检查存活生成类。
JFR ClassDefine 在本轮没有记录这些 hidden class，因此没有用它推算生成类数量。

| Loader | OFF（每次） | ON（每次） | ON 对应类型 |
| --- | ---: | ---: | --- |
| Fabric | 0 | 1 | `KeySetResponse` |
| Forge | 0 | 6 | `InjectorOptions`、`OverwriteOptions`、`MixinConfig`、`ReferenceMapper`、`KeySetResponse`、`MinecraftProfilePropertiesResponse` |

它们主要属于 Mixin 配置和认证响应。观察到的生成类没有覆盖配方、标签和 loot 的主要加载对象。

## 范围与限制

- 测试使用当前开发依赖集合和独立空白世界，无玩家、无成熟 AE 网络或生产线。
  结果适用于这里的启动和维护操作，不能直接推算生产存档的 TPS 或玩家交互收益。
- 同一宿主机上保留了原有生产服务器和 IDE 等进程；没有停掉其他任务，也没有做 CPU 隔离。
- 初次准备已预热文件系统缓存，因此这些数据不是机器冷启动的磁盘性能。
- Forge 每次正式运行均有相同的客户端类侧别错误，以及
  `expatternprovider:blocks/ex_emc_interface` loot 表引用缺失错误（启动与三次重载各一次）。
  两组错误一致且均完成重载，测量包含该开发配置的解析与报错成本。
- 认证库存在异步响应处理；只凭几个生成类不能推断长期真实使用频率。
- 本次仅切换 POJO 开关。Component 快速路径在两组均开启，不能用这些数据测量 Component 自身收益。

## 本地证据与复现材料

原始材料保留在 `.tmp/gson-server-ab/`，不加入版本控制：

- `analysis.json`：12 次运行、阶段计时、CPU 时间、JFR 汇总、输入哈希及日志校验。
- `runs/*-pair*/recording.jfr`：各运行的 JFR 文件。
- `runs/*-pair*/result.json`、`profile.tsv`、`classes.txt`、`stdout.log`：分项原始数据。
- `seeds/fabric`、`seeds/forge`：正式对照的源存档快照；每次运行的可写副本在测量后清理。
- `export.init.gradle`、`run.py`、`batch.py`、`JfrSummary.java`、`analyze.py`、`profile.jfc`：本次导出、驱动及分析脚本。

Fabric 输入 SHA-256 及 Forge 输入 SHA-256 保存在每次 `result.json` 的 `input_sha256`。
分析脚本验证了同一 loader 的六次输入哈希完全一致、代码 revision 一致、每组样本数为三。

本地重新分析现有数据：

```bash
python3 .tmp/gson-server-ab/analyze.py
```

如需追加对照，可在当前构建产物和这些本地材料仍存在时使用新的运行标签：

```bash
python3 .tmp/gson-server-ab/run.py forge true forge-extra-on
python3 .tmp/gson-server-ab/run.py forge false forge-extra-off
```

上述额外标签不会被当前只选择 `*-pair*` 的汇总脚本自动纳入原始 12 次结果。
重新编译或切换版本后应重新导出启动配置、准备同一输入快照，并将新的实验单独汇总。

## 补充：完整重载调用栈的优化优先级

进一步分析三个 Forge OFF 运行的九次重载，取消 Gson 栈过滤，共得到 1,219 个 Java 执行样本，
其中服务端主线程样本为 925 个（75.9%）。按完整调用栈的特征归类：

| 路径 | 样本占比 | 具体迹象 |
| --- | ---: | --- |
| 方块状态/形状缓存重建 | 28.5% | `BlockStateBase.Cache`、`Shapes.joinIsNotEmpty`、`IndirectMerger` |
| GTCEu 路径合计 | 28.5% | 配方索引、配方处理及 Codec 等 |
| 其中：GTCEu 配方索引 | 12.3% | `StagingRecipeDB`，频次统计中的 `getInt`、`containsKey`、`put`、rehash |
| JSON 资源扫描 | 14.3% | `SimpleJsonResourceReloadListener.scanDirectory`，文件读取和树解析 |
| IE 回收配方计算 | 7.7% | `ArcRecyclingCalculator.run` 及配方集合遍历 |

“其中”一行包含在 GTCEu 合计内，不能重复相加。这是 Java 采样归因，不是精确墙钟耗时比例，
也不表示对应路径可以全部移除。

更有针对性的候选方案：

1. **GTCEu 配方索引先做小范围验证。** `inputFrequencies` 使用 `mergeInt(input, 1, Integer::sum)`，
   采样显示它经过多次 Map 查询；验证用具体 map 的单次累加操作替换，再测索引构建和整体重载。
   不将 28.5% 的全部 GTCEu 成本归因于这一处。
2. **方块形状缓存作为较大潜力目标。** 验证共享、不可变形状上的相同支持面/相交判断能否复用。
   不能直接跳过标签重载后的所有状态缓存重建；动态形状、标签和配置依赖需要保留正确失效。
3. **精简资源读取的缓冲与分配。** 分配样本反复出现 `BufferedReader`、`StreamDecoder` 和
   `JsonReader` 的数组；先验证小 JSON 的多层缓冲成本，再考虑生命周期受控的缓冲复用。
4. **再考虑增量重载。** 只有明确资源内容、资源包顺序、标签、条件和配置依赖之后，
   才能跳过未变化的数据与派生索引。按文件名永久缓存解析结果不足以保证正确性。

`Content.codec(capability)` 也出现在分配样本中；按 capability 缓存 Codec 是候选之一，
但它捕获了 `ChanceLogic.getMaxChancedValue()` 等默认值，需要随相应配置版本失效。

完整样本分析脚本保留在 `.tmp/gson-server-ab/FullReloadProfile.java`。
上述候选尚未实施，没有新增收益数字；这些分析针对重载，不替代带真实生产线的 TPS 测试。
