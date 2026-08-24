---
navigation:
  parent: index.md
  title: ME 网络日志记录器
  icon: network_logger
item_ids:
  - ae2isallyouneed:network_logger
---

# ME 网络日志记录器

ME 网络的"黑匣子"：持续记录网络发生的事件，供事后排查与审计。

## 行为

- 每个网络最多 1 个有效记录器；放置多个时只有第一个工作。
- 不占用频道，闲置耗电 0.5 AE/t。
- 记录内容：拓扑 / 频道变化、设备上下线、能量通断、合成任务事件。
- 环形缓冲 4096 条，界面打开时定位到最后一条。
- 日志按记录器的 `loggerId` 持久化在存档中。

## 拆卸规则

- **扳手**拆除：保留 `loggerId`，历史日志跟着记录器走。
- **镐子**拆除：连同对应日志文件一起删除。

## 导出

可在界面中把全部日志下载到客户端的 `ae2isallyouneed/network-logs/` 目录，用任何文本工具离线分析。
