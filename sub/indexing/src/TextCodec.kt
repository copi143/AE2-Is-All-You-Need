package allyouneed.indexing

/**
 * 编码器顶层类型：查询与构建的统一入口。
 *
 * 维护说明：`Int` 路径与字节路径故意拆成两个子接口，而不是共用
 * `encode(): IntArray` 再内部转字节——后者会有两套真相源（改一处忘另一处）
 * 且构建期仍要先分配 `IntArray(4n)`，省不掉峰值。索引纯内存、不落盘，
 * 所以允许 breaking：`utf8` 只保留字节版，不再提供 `IntArray` 版。
 */
sealed interface Codec {
    val name: String
}

/** 以符号（[Int]）为单元的编码器：utf16 / utf32。 */
interface TextCodec : Codec {
    /** 分隔符符号，恒等于最大有效符号 + 1。 */
    val separator: Int

    /** 编码后符号取值范围为 `[0, separator)`。 */
    fun encode(text: String): IntArray
}

/**
 * 以字节为单元的编码器：目前仅 utf8。
 *
 * 约定（8bit 直存，无 `+2` 重映射）：
 * - `0x00` 为唯一最小终结符，只出现在拼接文本末尾；
 * - `0xFF` 为文档分隔符；
 * - 有效载荷为 `0x01~0xFE`。合法 UTF-8 永不产生 `0xFF`（最大到 `0xF4`），
 *   `0x00` 只来自 `U+0000`，因此含 `'\u0000'` 的文本直接抛异常。
 * 全字母表恰好 256 个符号，[WaveletMatrix] 只需 8 层。
 */
interface ByteCodec : Codec {
    /** 分隔符字节（无符号值），utf8 恒为 255。 */
    val separator: Int

    /** 编码为原始 UTF-8 字节，含 `U+0000` 时抛 [IllegalArgumentException]。 */
    fun encodeBytes(text: String): ByteArray
}

object Utf8Codec : ByteCodec {
    override val name = "utf8"
    override val separator = 255

    const val TERMINATOR = 0

    override fun encodeBytes(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            if (b == 0.toByte()) {
                throw IllegalArgumentException("utf8 文本含 U+0000，字节 0x00 被保留为终结符")
            }
        }
        return bytes
    }
}

object Utf16Codec : TextCodec {
    override val name = "utf16"
    override val separator = 0x10000

    override fun encode(text: String): IntArray {
        return IntArray(text.length) { text[it].code }
    }
}

object Utf32Codec : TextCodec {
    override val name = "utf32"
    override val separator = 0x110000

    override fun encode(text: String): IntArray = text.codePoints().toArray()
}
