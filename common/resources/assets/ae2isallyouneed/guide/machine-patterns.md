---
navigation:
  parent: index.md
  title: 机器样板系统
  icon: machine_pattern
item_ids:
  - ae2isallyouneed:machine_assembler
  - ae2isallyouneed:machine_pattern
  - ae2isallyouneed:pattern_encoding_terminal
  - ae2isallyouneed:pseudo_pattern
---

# 机器样板系统

让分子装配室直接执行"任意机器配方"的体系：不再需要把每台机器都搬进装配室旁边，一张样板就是一条生产线。

## 统一样板编码终端

<ItemLink id="pattern_encoding_terminal" />

AE2 样板编码终端的超集：在同一界面内编码处理样板、合成样板与[机器样板](#机器样板)，并配合 JEI / EMI 实现配方一键填入（在配方查看器中点击 + 号即可）。

## 机器样板

<ItemLink id="machine_pattern" />

描述"哪台机器、消耗什么、产出什么"的样板。编码完成后交给 [ME 分子装配室](#me-分子装配室)执行——由装配室模拟目标机器的加工过程，而不是把真实机器接入网络。

## ME 分子装配室

<ItemLink id="machine_assembler" />

机器样板的执行者。外观沿用经典分子装配室，但内部按机器样板的定义运行：输入原料 → 等待加工时间 → 输出产物，全程由 ME 网络供能。

## 伪样板

<ItemLink id="pseudo_pattern" />

不落地的轻量样板形态，用于在系统内部表示"待执行的合成意图"；玩家一般不会直接接触到它。
