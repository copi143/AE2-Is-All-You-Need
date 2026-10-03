# Gson / Component JSON 性能基线

基准使用 JMH 1.37 和项目的 Java 17 toolchain。JMH 只属于 benchmark source set，不打入模组。
JavaExec 工作目录是项目根目录下的 `.tmp`，JSON 结果和运行日志均写入该目录。

## 运行

首次缺少 JMH 缓存时去掉 `--offline` 下载依赖，之后可以离线执行。

```bash
# 重复创建 Gson 并绑定同一种 POJO：stock / cached / uncached
./gradlew :transformer:benchmark --offline --args='perf.GsonBindingBenchmark.newGsonAndBind -prof gc -foe true -rf json -rff gson-binding.json'

# 预热后复用同一 Gson 的读写
./gradlew :transformer:benchmark --offline --args='perf.GsonReadWriteBenchmark -prof gc -foe true -rf json -rff gson-read-write.json'

# 每个 fork 只测一次，包含首次 Gson 初始化与绑定，不做预热
./gradlew :transformer:benchmark --offline --args='perf.GsonColdBindingBenchmark -prof gc -foe true -rf json -rff gson-cold.json'

# 真实 Minecraft 类：plain / styled / translated / hover / fallback
./gradlew :common:benchmark --offline --args='perf.ComponentJsonBenchmark -prof gc -foe true -rf json -rff component.json'
```

常规默认参数为 2 个独立 JVM、3 × 1s 预热、5 × 1s 测量、单线程、256 MiB 堆。
可用 `-p shape=styled` 单独运行一个 Component 样本，用 `-v SILENT` 减少日志。
冷启动默认使用 8 个独立 JVM，各执行一次。

## 测量边界

- 隔离 ClassLoader 加载真实 Gson 字节码。`cached` / `uncached` 都实际变换 RTAF，
  不通过反射调用模拟每次序列化。`uncached` 用 `allyouneed.gsonfast.cache=false` 关闭类缓存。
- 适配器缓存只保存生成代码的构造句柄，每个 Gson 的字段适配器、访问句柄和实例构造器仍独立。
  每种 POJO 类型最多保留 64 种布局，额外布局正常生成但不缓存。
- Component 基准加载真实 Minecraft 类并注入 Style 访问器，确认选中了 `Injected` 实现。
  原版 Component 字符串入口不做改写，允许同一试验中校验原版与快速路径输出。
- ClassLoader 构建、实例准备、正确性校验在 setup 中进行，定时区只通过普通 Java 接口调用。
- `firstGsonAndBinding` 包含 Gson 初始化、首次类加载以及快速路径的变换/代码生成，
  **不等于 Minecraft 整体启动时间**。冷启动的 GC 分配数据还含 fork 启动/初始化开销，
  不用它估算单个适配器的分配量。
- `fallback` 的读取输入有重复 `bold` 字段，测量的是回退并重新解析的代价。
  写入时对象已经解析完成，因此该样本的写入不代表序列化回退。
- 这些都是固定小样本，不代表所有模组对象，也不能据此推算 TPS。

## 2026-10-03 本机基线

Temurin 17.0.20.1，JMH 1.37，Linux。常规测量使用较短参数：
`-wi 2 -i 3 -w 500ms -r 500ms -f 2 -prof gc`；冷启动使用上述默认参数。
未隔离宿主机其他进程。下列误差为 JMH 给出的 99.9% 置信区间半宽。

### 本轮缓存优化

新建 Gson 并绑定同一 POJO（含嵌套对象、集合和多种标量）：

| 模式 | μs/op | B/op |
| --- | ---: | ---: |
| 原版 Gson | 3.124 ± 0.188 | 6,320 |
| 快速路径，不缓存生成类 | 90.652 ± 12.993 | 124,259 |
| 快速路径，缓存生成类 | 7.330 ± 0.139 | 20,384 |

缓存相对不缓存，耗时降低约 91.9%，分配降低约 83.6%。
缓存后绑定成本仍高于原版；此收益是跨 Gson 重复绑定的收益，不是热态读写加速。

首次 Gson 初始化及绑定：原版 `21.015 ± 1.137 ms`，缓存路径 `46.844 ± 4.828 ms`，
不缓存路径 `47.019 ± 4.176 ms`。首次无缓存可命中，缓存并未消除代码生成的启动成本。

### 热态 POJO 读写

| 操作 | 原版 μs/op | 快速路径 μs/op | 原版 / 快速路径 B/op |
| --- | ---: | ---: | ---: |
| 读取 | 0.503 ± 0.098 | 0.532 ± 0.041 | 3,712 / 3,712 |
| 写入 | 0.700 ± 0.010 | 0.695 ± 0.021 | 1,536 / 1,536 |

当前样本没有测出明确的热态 POJO 收益；两者误差区间重叠。

### 现有 Component 快速路径

这部分记录修复正确性后的基线，不将其收益归因于本轮的适配器缓存。

| 样本 | 操作 | 原版 μs/op | 快速路径 μs/op | 原版 / 快速路径 B/op |
| --- | --- | ---: | ---: | ---: |
| 纯文本 | 读取 | 0.374 ± 0.006 | 0.196 ± 0.004 | 6,072 / 2,872 |
| 纯文本 | 写入 | 0.155 ± 0.014 | 0.115 ± 0.001 | 1,272 / 776 |
| 样式及 extra | 读取 | 0.908 ± 0.052 | 0.422 ± 0.037 | 10,312 / 3,688 |
| 样式及 extra | 写入 | 0.904 ± 0.038 | 0.543 ± 0.002 | 3,272 / 600 |
| 翻译及参数 | 读取 | 0.726 ± 0.019 | 0.311 ± 0.028 | 9,864 / 3,408 |
| 翻译及参数 | 写入 | 0.566 ± 0.017 | 0.368 ± 0.006 | 2,592 / 496 |
| Hover 文本 | 读取 | 0.767 ± 0.064 | 0.698 ± 0.035 | 13,240 / 9,960 |
| Hover 文本 | 写入 | 0.799 ± 0.016 | 0.628 ± 0.010 | 3,832 / 2,208 |
| 重复字段 | 读取 | 0.452 ± 0.008 | 1.499 ± 0.076 | 6,216 / 12,956 |
| 重复字段解析后的对象 | 写入 | 0.225 ± 0.010 | 0.165 ± 0.002 | 1,312 / 456 |

重复字段读取明显变慢，原因是先退出流式解析，再重读为树以保持原版语义。
Hover 读取的耗时区间也有重叠，暂不据此声称稳定加速。

本次原始数据保存在 `.tmp/gson-binding.json`、`.tmp/gson-read-write.json`、
`.tmp/gson-cold.json` 和 `.tmp/component-{plain,styled,translated,hover,fallback}.json`。
这些本地结果不提交；本表和运行命令用于后续对照。
