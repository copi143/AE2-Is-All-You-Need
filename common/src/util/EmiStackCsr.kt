package allyouneed.util

import dev.emi.emi.api.stack.EmiStack
import it.unimi.dsi.fastutil.objects.Object2IntMap

/**
 * `EmiRecipes$Manager` 的 CSR 索引对（stack→id 映射 + CSR 邻接）。
 * 独立顶层类而非 mixin 内部类：mixin 包内的类禁止被注入代码直接引用
 * （Mixin 0.8.5 的 IllegalClassLoadError），因此必须放在 mixin 包之外。
 */
class EmiStackCsr(@JvmField val stackIds: Object2IntMap<EmiStack>, @JvmField val csr: CsrIndex)
