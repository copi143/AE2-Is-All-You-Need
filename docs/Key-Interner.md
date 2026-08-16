# AEKey Interner（全局单例化）

让 AE2 的 `AEKey`（及第三方派生类）的**所有创建点**返回**同一实例**（`===` 成立），
使基于 identity 的 HashMap / `IdentityHashMap` 场景直接命中。

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

用 ASM `Analyzer(SourceInterpreter)` 按数据流把 `NEW <key>` 和消耗它的 `<init>` 配对
（三元、先存局部再构造、嵌套 `new` 都能配）。对不上的 `NEW` 打 WARN，不改。

```
NEW   <keyClass>
DUP
<构造参数，可含跳转>
INVOKESPECIAL <keyClass>.<init>(<desc>)V
INVOKESTATIC allyouneed/core/KeyInterner.intern(Ljava/lang/Object;)Ljava/lang/Object;
CHECKCAST <keyClass>
```

### 父类与 equals / hashCode

直接继承 `AEKey` 的类：`super` 改为 `AEKeyAsm`，`<init>` 里的 `AEKey.<init>` 改成 `AEKeyAsm.<init>`。
原 `equals`/`hashCode` 改名为 `asm$equals`/`asm$hashCode`（实现 `AEKeyAsm` 的抽象方法）。
`AEKeyAsm.equals` 为 `final` identity；`hashCode` 转发 `asm$hashCode()`。
`dropSecondary` 改名为 `asm$dropSecondary`；`AEKeyAsm.dropSecondary` 为 `final` 并缓存结果。
intern 表对 `AEKeyAsm` 走 `asm$*`。扫描排除 `AEKeyAsm` 自身。

## KeyInterner

```kotlin
object KeyInterner {
    fun intern(key: Any): Any   // AEKeyAsm 走 asm$*，否则 equals/hashCode
    fun size(): Int
}
```

不反射构造。ASM 只在 `new` 之后调用 `intern`。

## KeyClassScanner

- `scanKeyClasses`：读 `(name, superName)`，判定继承 `appeng/api/stacks/AEKey`
- `findNewCallSites`：含 `NEW <keyClass>` 的类
- 纯 ASM 字符串常量，零 AE2 依赖

## 与 KeyIdHolder

intern 后同一内容只有一个实例，`AEKeyMixin` 上的 `primaryId` / `secondaryId`
与 `AEKeyAsm` 上的 `dropSecondary` 缓存天然共享。intern 是源头单例化，Mixin 是实例上的身份缓存。
