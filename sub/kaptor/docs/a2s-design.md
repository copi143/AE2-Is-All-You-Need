# a2s 脚本系统设计文档

> 面向玩家的 AE2 专用脚本语言。脚本绑定到玩家自己的 AE2 网络，用于处理游戏内事件、操作 ME 网络存储与合成。

## 一、定位与目标

| 维度 | 决策 |
|------|------|
| 目标用户 | 普通玩家 + 模组开发者，两者兼顾 |
| 语言风格 | 类 Kotlin 简化版 |
| 与 kaptor 关系 | 完全独立，保留现有 `.kts`/`.kt` 系统不变 |
| 脚本扩展名 | `.a2s` |
| 绑定粒度 | 每个玩家 AE2 网络一个引擎实例 |
| 资源解析 | JIT 编译期静态解析（编译时 AE2 资源表已就绪） |
| 依赖方向 | kaptor 定义接口，common 模块注入 AE2 实现 |

---

## 二、语言语法

### 2.1 资源引用（反引号）

反引号内的内容为资源引用，无反引号的裸标识符为变量/函数名。词法上反引号天然消歧，无需处理 `/`、`:` 的运算符冲突。

**资源类型动态来源于 AE2 注册表**（`AEKeyType`），非语法硬编码。类型名采用大写驼峰（`Item`/`Fluid`/`Energy`/`Mana`/`Virtual`），反引号前缀小写且大小写不敏感，两者统一经 `ResourceResolver.resolveKeyType()` 解析。

```
`diamond`                 → item|minecraft:diamond（默认 item + minecraft）
`minecraft:diamond`       → 默认类型推断（无冲突时）
`item|minecraft:diamond`  → 全限定（小写前缀，大小写不敏感）
`fluid|minecraft:water`   → 流体
`energy|gtceu:iv`         → 自定义 EnergyKey
`mana|...`                → 自定义 ManaKey
`virtual|...`             → 自定义 VirtualKey
```

省略类型前缀时默认推断类型；若有冲突（同一资源名对应多种类型）则强制要求写明前缀。

**数量**：资源 `* 整数` 得到带数量的 Stack。

```
`diamond` * 10            // 10 个钻石的 Stack
```

**类型标注**（大写驼峰）：

```
val x: Item
val y: Fluid
val z: Energy
```

未注册的资源类型名在编译期报错「未知类型」。

### 2.2 事件声明（特殊 class）

事件是不可变数据类：构造参数只能 `val`，无继承、无 `var`，但允许方法（含单表达式简写）。

**事件名采用大驼峰命名**（`UpperCamelCase`），如 `MyEvent`、`PlayerRightClick`、`MeNetworkExtract`。

```
event MyEvent(val arg1: i32, val arg2: i32) {
    fun sum() = arg1 + arg2        // 单表达式简写

    fun total(): i32 {              // 完整块
        return arg1 + arg2
    }
}
```

### 2.3 事件处理与发送

```
on XxxEvent { e ->
    println(e.arg1)
    post MyEvent(1, 2, 3)          // 入队，延迟 1 tick 发送
}

before XxxEvent { e -> ... }
after XxxEvent { e -> ... }
```

**阻止操作**（仅内置拦截点，如 AE2 存储取出/存入）：

```
on MeNetworkExtract { e ->
    if (e.item.key == `diamond`) {
        e.deny()                    // 设置 isDenied 标记
    }
}
```

### 2.4 变量 / 函数 / 控制流（类 Kotlin 简化）

```
val x = 10                          // BigInt（默认大整数）
var y = 20                          // BigInt
fun foo(n: i64): i64 = n + 1        // 参数强制类型标注
if / when / for / while / return / break / continue
try / catch / finally / throw
lambda：{ e -> ... }
listOf(1, 2, 3)                     // 集合用 listOf 函数
```

补充约定：

- **顶层只允许声明**（`val`/`var`/`fun`/`event`）与事件处理器，**不允许执行语句**（无副作用）。
- **函数参数强制类型标注**（编译器需确定参数类型）。
- **完整空安全**：`String?` 可空类型、`?.`、`?:`、`!!` 全支持。
- **字符串模板与转义**：`${expr}` 插入表达式、`$name` 插入变量；`$$` 转义为字面 `$`（如 `"$$name"` 输出字面 `$name`，不被当作变量引用）。

---

## 三、事件处理阶段语义

### 3.1 三个阶段

```
dispatch(event):
  1. before 阶段：广播所有 before 处理器（纯观察，无 deny/handled 能力）
  2. on 阶段：按注册顺序依次响应（唯一可做决策的阶段）
  3. after 阶段：无论如何都广播所有 after 处理器（finally 语义）
```

### 3.2 on 阶段的四种结局

| 结局 | 行为 |
|------|------|
| 正常返回 | 继续下一个 on 处理器 |
| `e.handled()` | 停止 on 链传播，后续 on 处理器不执行 |
| `e.deny()` | 设置 `isDenied` 标记（正交，不停止传播） |
| 抛出异常 | 捕获隔离 + 记日志 + 继续下一个 on 处理器 |

### 3.3 能力矩阵

| 能力 | before | on | after |
|------|--------|-----|-------|
| 观察/通知 | ✅ | ✅ | ✅ |
| `handled()` 停止传播 | ❌ | ✅ | ❌ |
| `deny()` 阻止操作 | ❌ | ✅（仅内置拦截点） | ❌ |
| 异常隔离 | ✅ | ✅ | ✅ |

### 3.4 语义说明

- **deny() 与 handled() 正交**：`deny()` 只设置 `isDenied` 业务否决标记，不停止传播；`handled()` 只停止传播，不设置 deny。
- **before/after 是纯观察者**，on 是唯一能做决策的阶段。
- **异常隔离**：一个 on 处理器抛异常不中断后续处理器，也不影响 after 阶段执行。这对普通玩家脚本至关重要（烂脚本不拖垮整个链）。
- **多脚本同事件注册三阶段处理器**：若某 on 处理器 `handled()`，则同事件的 before 和 after 处理器照常收到通知，但后续 on 处理器不会收到——这是正常行为。

---

## 四、核心架构

### 4.1 多实例引擎

kaptor 是 `object` 单例；a2s 是可实例化类，**每个玩家 AE2 网络一个实例**。

```
class A2sEngine {
    val eventRegistry: EventTypeRegistry   // 内置 + 本网络自定义事件
    val scriptManager: A2sScriptManager     // 脚本加载/编译/热重载
    val eventQueue: A2sEventQueue           // post 队列
    val sandbox: A2sSandbox                 // 沙盒
}
```

每个实例隔离：事件类型注册表、脚本集合、事件队列、沙盒状态。**实例内脚本共享自定义事件**。

- 内置事件（如 `PlayerRightClick`、`ServerTick`）由引擎预注册，脚本直接 `on` 即可，无需声明。
- 自定义事件由脚本 `event` 声明，同一引擎实例内的脚本可跨脚本共享。

### 4.2 post 事件队列

```
post MyEvent(...) → 写入 per-engine 队列（不立即执行）
ServerTickEvent END（Forge）/ END_SERVER_TICK（Fabric）→ flush
flush = drain 队列 → 逐个 dispatch → 新 post 进入下一刻
```

**必定延迟 1 tick**，从根本上杜绝「事件处理器内再触发事件」导致的无限递归。

### 4.3 deny 分层

| 层 | 职责 |
|----|------|
| a2s 引擎 | 内置拦截点事件对象提供 `deny()`，设置 `isDenied`；分发完成后桥接层读取 |
| common（AE2）桥接 | 存储操作前调用分发，检查 `event.isDenied`，true 则阻止操作 |

内置拦截点（引擎预注册）：`MeNetworkExtract`、`MeNetworkInsert` 等 AE2 存储操作。

### 4.4 资源解析（接口 + 注入）

kaptor 不依赖 AE2，定义接口由 common 提供实现。JIT 编译期静态解析（编译时 AE2 资源表已就绪），冲突时强制要求写全限定。资源类型（`Item`/`Fluid`/`Energy`/`Mana`/`Virtual` 等）动态来源于 AE2 的 `AEKeyType` 注册表，非语法硬编码。

```kotlin
interface ResourceResolver {
    // 类型名（大小写不敏感）→ 抽象 key type 引用；未注册返回 null
    fun resolveKeyType(name: String): KeyTypeRef?
    // 反引号资源引用 → 具体资源
    fun resolve(prefix: String?, namespace: String?, path: String): ResolvedResource?
}

interface KeyTypeRef {          // opaque，隔离 AE2 依赖
    val name: String
}

sealed interface ResolvedResource {
    val key: String
    data class Key(override val key: String) : ResolvedResource
    data class Stack(override val key: String, val amount: Long) : ResolvedResource
}
```

common 模块实现时维护「脚本名 → AEKeyType」映射（大小写不敏感）：

```kotlin
override fun resolveKeyType(name: String) = when (name.lowercase()) {
    "item"    -> wrap(AEKeyType.items())
    "fluid"   -> wrap(AEKeyType.fluids())
    "energy"  -> wrap(EnergyKey.Type)
    "mana"    -> wrap(ManaKey.Type)
    "virtual" -> wrap(VirtualKey.Type)
    else      -> null
}
```

---

## 五、沙盒设计（多层）

```
A2sSandbox:
  instructionLimit      // 指令计数（沿用 kaptor 思路）
  recursionLimit        // 递归深度
  loopIterationLimit    // 循环迭代次数
  timeLimitMs           // 执行时间
  apiPolicy             // API 白名单/黑名单
```

编译期在循环入口、函数调用入口、API 调用入口注入检查。

---

## 六、文件结构（kaptor 下新包）

```
sub/kaptor/
├── antlr/
│   ├── A2sLexer.g4              # 词法（RESOURCE_REF 反引号 token）
│   └── A2sParser.g4             # 语法（类 Kotlin 简化）
└── src/kaptor/a2s/
    ├── parser/
    │   └── A2sVisitor.kt        # ParseTree → IR
    ├── ir/
    │   ├── A2sIr.kt             # 事件/资源/Stack/post 等 IR 节点
    │   └── A2sType.kt           # i32/i64/.../BigInt/Rational/Item/Stack/List 类型
    ├── compiler/
    │   ├── A2sCompiler.kt
    │   ├── A2sExpressionCompiler.kt
    │   └── A2sStatementCompiler.kt
    ├── runtime/
    │   ├── A2sEngine.kt         # 多实例引擎
    │   ├── A2sEventRegistry.kt  # 事件类型注册（内置+自定义）
    │   ├── A2sEventQueue.kt     # post 队列 + flush
    │   ├── A2sScriptManager.kt  # 加载/编译/热重载
    │   ├── A2sSandbox.kt        # 多层沙盒
    │   └── A2sEventObject.kt    # 事件基类（含 isDenied、deny()）
    └── resource/
        └── ResourceResolver.kt  # 接口（common 注入实现）
```

---

## 七、数值系统

### 7.1 类型命名（Rust 风格）

| 类型 | 含义 | 字节码 |
|------|------|--------|
| `i32` | 32 位有符号整数 | int |
| `i64` | 64 位有符号整数 | long |
| `u32` | 32 位无符号整数 | int（value class） |
| `u64` | 64 位无符号整数 | long（value class） |
| `f32` | 32 位浮点 | float |
| `f64` | 64 位浮点 | double |
| `BigInt` | 任意精度整数（默认） | BigInteger |
| `Rational` | 分数（默认） | 自定义 |

无符号 `u32`/`u64` 借鉴 Kotlin `value class` 实现：编译期独立类型，运行时包装原生 `int`/`long`，零开销；比较/除法/位移按无符号语义生成。

### 7.2 默认字面量

无后缀字面量默认使用大数/有理数，保证永不溢出、不失真：

```
123        → BigInt（任意精度）
3.14       → Rational(157/50)（立即约分）
```

### 7.3 显式定长后缀

后缀 `_类型` 指定定长类型（性能敏感场景用）。`_` 后跟字母为类型后缀，`_` 后跟数字为千分位分隔符：

```
123_i32 / 123_i64 / 123_u32 / 123_u64
3.14_f32 / 3.14_f64
1_000_000  → 千分位，仍为 BigInt
```

### 7.4 Rational 显示与约分

- **立即约分**：构造时即约分，`==` 直接比较分子分母，`1/3 + 2/3` 直接等于 `1`。
- **显示格式**：`157/50 (3.14)` —— 完整分数 + 括号内自适应精度小数。

### 7.5 运算提升规则

```
BigInt op BigInt      → BigInt
Rational op Rational  → Rational
BigInt op Rational    → Rational
i64 op BigInt         → BigInt（定长提升为大数）
i64 op i64            → i64（定长保持）
```

### 7.6 溢出与转换

- 定长整数（`i64`）溢出**静默截断**（JVM/Rust release 语义）。
- 类型转换用**方法调用**：`x.toI64()`、`x.toBigInt()`、`x.toRational()`。

### 7.7 Stack 运算

```
`diamond` * 10            → Stack（构造）
Stack + Stack             → 同 key 合并数量，不同 key 报错
Stack - Stack             → 同 key 相减数量
Stack == Stack            → 比较 key + 数量
```

---

## 八、类型系统

```
标量：i32, i64, u32, u64, f32, f64, Boolean, String
大数：BigInt（默认整数）, Rational（默认小数）
资源：Item, Fluid, Energy, Mana, Virtual（动态来源于 AE2 注册表，非硬编码）
容器：Stack（key + 数量）, List<T>
特殊：Any, Null, Unit
```

- 资源类型动态来源于 AE2 的 `AEKeyType` 注册表，其他模组也可注册新类型。脚本中未注册的类型名在编译期报错。
- `` `diamond` `` 是 **Item 类型**，`` `diamond` * 10 `` 是 **Stack 类型**，编译器严格区分二者。
- **完整空安全**：`String?` 可空类型、`?.`、`?:`、`!!` 全支持。

---

## 九、实施步骤

| 步骤 | 内容 | 预计 |
|------|------|------|
| 1 | ANTLR 词法 + 语法（`RESOURCE_REF` 反引号 token、`event`/`on`/`before`/`after`/`post`/`handled`/`deny` 关键字） | 3-4 天 |
| 2 | A2sVisitor（ParseTree → IR，全语句/表达式覆盖） | 3-4 天 |
| 3 | 编译器（ASM 编译、资源字面量、`*` 数量、事件字段 `getfield`） | 3-4 天 |
| 4 | 多实例引擎 + 队列 + 沙盒（`A2sEngine`、post flush、deny 标记、多层沙盒） | 3-4 天 |

---

## 十、完整示例

### 10.1 基础事件处理

```a2s
on PlayerRightClick { e ->
    if (e.itemStack.key == `diamond`) {
        e.player.sendMessage("你右键点击了钻石!")
    }
}
```

### 10.2 自定义事件与 post

```a2s
event MyEvent(val count: i32) {
    fun doubled() = count * 2
}

on PlayerRightClick { e ->
    post MyEvent(e.itemStack.count)
}

on MyEvent { e ->
    println("收到事件，count 翻倍 = ${e.doubled()}")
}
```

### 10.3 阻止操作

```a2s
on MeNetworkExtract { e ->
    if (e.item.key == `diamond`) {
        e.deny()
    }
}
```

### 10.4 错误处理

```a2s
on MeCraftingComplete { e ->
    try {
        println("合成完成: ${e.result}")
    } catch (err) {
        println("处理失败: ${err.message}")
    }
}
```

### 10.5 数值系统

```a2s
on PlayerRightClick { e ->
    val count = 123              // BigInt
    val ratio = 3.14             // Rational(157/50)
    val exact = 1 / 3            // Rational(1/3)，永不失真
    println(exact)               // 输出: 1/3 (0.3333)

    val fast = 123_i64           // 定长 i64
    val f = 3.14_f32             // 定长 f32

    val total = `diamond` * 10 + `diamond` * 5   // Stack 合并 → 15 个
    println(total)               // key 相同，数量合并
}
```
