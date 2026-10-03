package allyouneed.client.integration.emi.fold

/**
 * 折叠组背景色：内置 AE2 渐变的 5 个等距档位（与
 * [AE2_GRADIENT][allyouneed.resgen.AE2_GRADIENT] 的 0/4/8/12/16 位同色，
 * 数值 snapshot 于此，不读外部文件），按组键哈希稳定循环。
 *
 * 纯 Kotlin，无 Minecraft / EMI / Gson / 文件 IO 依赖，可单元测试。
 */
object EmiFoldPalette {
    private val PALETTE = listOf(
        0xFF5EB6E2.toInt(),
        0xFF26CE9E.toInt(),
        0xFFB7B156.toInt(),
        0xFFEA808A.toInt(),
        0xFFCC83D7.toInt(),
    )

    /** 按组键哈希稳定地循环取色。 */
    fun colorFor(groupKey: String): Int =
        PALETTE[Math.floorMod(groupKey.hashCode(), PALETTE.size)]
}
