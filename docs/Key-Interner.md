# AEKey Interner（全局单例化）

让 AE2 的 `AEKey`（及第三方派生类）的**所有创建点**返回**同一实例**（`===` 成立），
使基于 identity 的 HashMap / `IdentityHashMap` 场景直接命中，同时消除 `AEItemKey.maxStackSize`
缓存字段在不同实例间不一致的问题。

## 架构

```
Forge 发行单 jar：外壳是 ITransformationService（PLUGIN），内嵌 META-INF/mod/game.jar
由 SelfModLocator 抽出后当普通模组加载。开发时仍把 transformer 拷到 run/mods。

fabric jar
  ├─ include(:transformer) + PreLaunch
  ├─ allyouneed.core.KeyInterner
  └─ @Mod 业务
```

**时序**：插件层 `onLoad` → 扫描 mods 注册 `ITransformer` → AE2 加载 `AEItemKey` →
`NEW+<init>` 后插入 `KeyInterner.intern`。

Fabric：PreLaunch 包装 Knot `GameTransformer`，对目标类实时调用同一套 ASM。

## 变换规则（NewCallTransformer）

从 `NEW+DUP` 起按栈深度找配对的 `<init>`（允许 Label、三元跳转、invoke、局部计算）。
嵌套 `new Key(...)` 各自配对。对不上的 `NEW` 打 WARN，不改。

```
NEW   <keyClass>
DUP
<构造参数，可含跳转>
INVOKESPECIAL <keyClass>.<init>(<desc>)V
INVOKESTATIC allyouneed/core/KeyInterner.intern(Ljava/lang/Object;)Ljava/lang/Object;
CHECKCAST <keyClass>
```

### `AEItemKey.maxStackSize` 缓存污染

`AEItemKey.of(ItemStack)` 会在 `of(item, tag)` 的返回值上写
`ret.maxStackSize = stack.getMaxStackSize()`。intern 后该返回值是共享实例，
写入会污染其它 stack 的缓存。处理：对该类 `of(ItemStack)` 方法中的
`PUTFIELD appeng/api/stacks/AEItemKey.maxStackSize` 替换为 `POP2`。
`getMaxStackSize()` 首次调用时 `maxStackSize == -1` 会自动兜底计算。

### equals / hashCode

对 AEKey 子类：原 `equals`/`hashCode` 改名为 `asm$equals`/`asm$hashCode`，并实现
`ContentIdentity`。新 `equals` 为 `this == o`；新 `hashCode` 转发 `asm$hashCode()`。
intern 表按 `asm$equals`/`asm$hashCode` 查找。

## KeyInterner

```kotlin
object KeyInterner {
    fun intern(key: Any): Any   // ContentIdentity 走 asm$*，否则 equals/hashCode
    fun size(): Int
}
```

不反射构造。ASM 只在 `new` 之后调用 `intern`。

## KeyClassScanner

- `scanKeyClasses`：读 `(name, superName)`，判定继承 `appeng/api/stacks/AEKey`
- `findNewCallSites`：含 `NEW <keyClass>` 的类
- 纯 ASM 字符串常量，零 AE2 依赖

## 与 KeyIdHolder

intern 后同一内容只有一个实例，`AEKeyMixin` 上的 `primaryId` / `secondaryId` /
`cachedSecondaryDropped` 天然共享。intern 是源头单例化，Mixin 是实例上的身份缓存。
