# AE2 合成计算（后台调度 / 快照）知识库

> 本模组用 `ACraftingCalculation` 替换原版 `CraftingCalculation`，以支持自适应概率样板，
> 并在专用线程池上并行处理多玩家合成请求。

---

## 1. UI「正在计算」卡死的根因

客户端 `CraftConfirmScreen` 在 `menu.getPlan() == null` 时显示 `GuiText.CalculatingWait`。

服务端 `CraftConfirmMenu.broadcastChanges()` 逻辑：

```text
job != null && job.isDone()
  → result = job.get()
  → plan = CraftingPlanSummary.fromJob(...)
  → sendPacketToClient(CraftConfirmPlanPacket)
```

因此：**`Future` 一直不 complete ⇒ 永远「正在计算」**。

常见原因：

| 原因 | 说明 |
| --- | --- |
| 后台任务未真正执行 | 使用 `Dispatchers.Default` 等公共池，在 Forge 下 ClassLoader / 饥饿导致不调度 |
| 后台访问 live 网格死锁 | AE2 明确：合成模拟在独立线程时，任何世界/网格访问都可能死锁 |
| 破坏原版 pause 握手 | 原版 `handlePausing` ↔ `simulateFor` 用 monitor 握手；半吊子改写会卡 TickHandler 锁 |
| `Future` 异常未 complete | 调度器 catch 不完整时 future 悬挂 |

---

## 2. 原版 AE2 执行模型

```text
主线程 beginCraftingCalculation
  → new CraftingCalculation(...)   // 构造时 NetworkCraftingSimulationState 拷贝库存
  → CRAFTING_POOL.submit(job::run)

后台 run():
  registerCraftingSimulation(level, this)
  handlePausing()  // 等主线程 simulateFor 放行
  computePlan()
  finish()

主线程 TickHandler 每 tick:
  synchronized(craftingJobs) {
    cj.simulateFor(micros)  // 放行后台一小段时间，自己 wait 到后台 pause
  }
```

要点：

- **库存**在构造时已拷贝进 `KeyCounter`（值拷贝，不是 `getCachedInventory()` 引用）。
- **样板**原版在后台按需查 live `ICraftingService`（依赖 pause，避免长时间占着网格）。
- UI **只依赖 Future**，不依赖 TickHandler；TickHandler 只做时间片。

`IStorageService.getCachedInventory()` 文档：*Does not return a copy. Do not modify!*

---

## 3. 本模组目标模型

```text
主线程 beginCraftingCalculation (CraftingServiceMixin)
  1. new ACraftingCalculation(...)
       - MeInventorySnapshot.copy → 完整 KeyCounter 值拷贝
       - CachedCraftingService.snapshot → 全部 pattern / emit / fuzzy 数据
       - 构建 ACraftingTreeNode（可用缓存服务）
  2. AE2TaskScheduler.submit(job::run)   // 专用固定线程池，CPU/2

后台 run():
  - 不再 registerCraftingSimulation
  - 仅使用快照数据计算
  - handlePaUSING: 只检查 Thread.interrupted + yield
  - finally: done=true；调度器保证 Future complete/completeExceptionally
```

硬性约定：

> **提交到 `AE2TaskScheduler` 之前，必须在调用线程完成 ME 库存与合成样板的值拷贝。  
> 后台任务禁止持有/访问 live `IStorageService` / live `ICraftingService`。**

---

## 4. 组件职责

| 类 | 路径 | 职责 |
| --- | --- | --- |
| `AE2TaskScheduler` | `allyouneed.logic` | 全局后台池，并行度 `max(1, cores/2)`；`CompletableFuture` |
| `MeInventorySnapshot` | `logic.crafting` | 从 storage 值拷贝到新 `KeyCounter` |
| `CopiedNetworkSimulationState` | 同上 | 仅持有拷贝后的 `KeyCounter`，无 storage 引用 |
| `CachedCraftingService` | 同上 | **构造时**快照全部 craftable/emittable/pattern；后台只读缓存 |
| `ACraftingCalculation` | 同上 | 自适应合成计算；快照后纯计算 |
| `CraftingServiceMixin` | `mixin.ae2` | 替换 `beginCraftingCalculation` |

---

## 5. `AE2TaskScheduler` 设计要点

- **不要**用 `Dispatchers.Default` 跑长时间阻塞合成（Forge ClassLoader / 公共池问题）。
- 使用 `Executors.newFixedThreadPool(parallelism)`，守护线程名 `AE2AYN-Worker-n`。
- `CompletableFuture.supplyAsync(callable, executor)`：
  - `cancel(true)` 可中断工作线程
  - 异常路径 `completeExceptionally`，避免 Future 悬挂
- 后续其它后台任务（结构扫描等）也走同一调度器。

---

## 6. Kotlin 注意：`handlePaUSING`

AE2 原版方法在源码中为 `handlePausing`，部分映射/历史代码写作 `handlePaUSING`。

- Kotlin **源码标识符区分大小写**；定义与调用必须拼写一致。
- JVM 字节码可能规范化，但编译期以源码名为准。
- 本模组统一使用 `handlePaUSING()`，与树节点调用一致。

---

## 7. 排查清单

1. 日志是否有 `AE2TaskScheduler` / `ACraftingCalculation` 的 start/end？
2. `Future.isDone()` 是否永远 false？（调度/死锁）还是 complete 后 UI 仍无 plan？（发包/摘要）
3. 后台线程栈是否卡在 grid/storage/world？
4. 取消任务后是否 `Thread.interrupted` 被 `handlePaUSING` 响应？

---

## 8. 相关原版类

- `appeng.crafting.CraftingCalculation`
- `appeng.crafting.inv.NetworkCraftingSimulationState`
- `appeng.me.service.CraftingService`（`CRAFTING_POOL`）
- `appeng.hooks.ticking.TickHandler#simulateCraftingJobs`
- `appeng.menu.me.crafting.CraftConfirmMenu`
- `appeng.client.gui.me.crafting.CraftConfirmScreen`
