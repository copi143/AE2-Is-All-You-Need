# ME 网络日志记录器

每个 ME 网络最多 **1 个**有效记录器。多则全废，全部停止写入。

## 行为

- 不占频道，闲置耗电 0.5 AE/t。
- 记录拓扑/频道、设备上下线、能量通断、合成任务。
- 日志按记录器 24-bit `loggerId` 存在 `<world>/data/ae2isallyouneed/logs/`。
- 扳手拆除：保留 `loggerId`，日志跟着走。
- 镐子拆除：删除对应日志文件。
- 环形缓冲 4096 条。
- 条目时间戳为服务端 UTC 毫秒；客户端按本地时区显示。
- 打开界面时定位到最后一条。
- 可下载全部日志到客户端 `ae2isallyouneed/network-logs/`。

## 唯一性

`NetworkLogService`（`GridServices.register`）在 `addNode`/`removeNode` 后下一 tick 复检数量。网格合并没有官方事件；被吞节点会 `addNode` 进幸存 Grid，自然复检。

## 挂钩

| 类别 | 来源 |
| --- | --- |
| 停电 / 控制器 / 频道需求 / CPU | `GridHelper.addGridServiceEventHandler`（boot 起停不记，拓扑重算太吵） |
| 设备加入/离开 | `addNode`/`removeNode`；同 tick 拆网再入网的对消，不记 |
| 设备通电/频道 | mixin `GridNode.notifyStatusChange`；boot / 刚换网时忽略 |
| 合成提交 | mixin `CraftingService.submitJob` |
| 合成开始/完成/取消 | mixin `CraftingCpuLogic.notifyJobOwner` |

## GUI

Compose（`AeComposeScreen` + `McVirtualColumn`）。日志页通过 `@GuiSync` + `PacketWritable` 记录 `NetworkLogPage` 同步；全量下载走 `NetworkLogDump`。
