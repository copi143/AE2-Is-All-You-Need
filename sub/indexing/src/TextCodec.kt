package allyouneed.indexing

/**
 * 编码器顶层类型：查询与构建的统一入口。
 *
 * 约定（所有路径统一，载荷均为原始值存储，不做 `+2` 重映射）：
 * - `0` 为唯一最小终结符，只出现在拼接文本末尾；编码器保证输出不含 `0`；
 * - 分隔符为二进制全 `1`（`Byte` 为 `0xFF`/`-1`，`Short` 为 `0xFFFF`/`-1`，
 *   `Int` 为 `0xFFFFFFFF`/`-1`），只出现在文档边界；
 * - 查询模式只含载荷，绝不会命中分隔符/终结符，故不会跨文档匹配。
 *
 * 冲突说明：
 * - utf8：合法 UTF-8 永不产生 `0xFF`（最大到 `0xF4`），`0x00` 只来自 `U+0000`，
 *   因此含 `U+0000` 的文本直接抛异常；
 * - utf16：`U+FFFF`（`0xFFFF`）与分隔符重合，与 `U+0000` 同样直接抛异常；
 * - utf32：码点范围 `1~0x10FFFF` 永不等于 `-1`，只需拒绝 `U+0000`。
 * 全字母表分别为 256 / 65536 个符号，[WaveletMatrix] 只需 8 / 16 层。
 */
sealed interface Codec<T, U> {
    val name: String
    val alphabetSize: kotlin.Int
    val separator: T
    fun encode(text: String): U
    fun decode(data: U, start: kotlin.Int, end: kotlin.Int): String

    interface Byte : Codec<kotlin.Byte, ByteArray>
    interface Short : Codec<kotlin.Short, ShortArray>
    interface Int : Codec<kotlin.Int, IntArray>

    object UTF8 : Byte {
        override val name = "utf8"
        override val alphabetSize: kotlin.Int = 256
        override val separator: kotlin.Byte = -1
        override fun encode(text: String): ByteArray = text.toByteArray(Charsets.UTF_8).also {
            it.any { b -> b == 0.toByte() } && throw IllegalArgumentException("文本包含 NUL，字节 0x00 被保留为终结符")
        }

        override fun decode(data: ByteArray, start: kotlin.Int, end: kotlin.Int): String =
            String(data, start, end - start, Charsets.UTF_8)
    }

    object UTF16 : Short {
        override val name = "utf16"
        override val alphabetSize: kotlin.Int = 65536
        override val separator: kotlin.Short = -1
        override fun encode(text: String): ShortArray = ShortArray(text.length) { i -> text[i].code.toShort() }.also {
            it.any { s -> s == 0.toShort() } && throw IllegalArgumentException("文本包含 NUL，字节 0x00 被保留为终结符")
        }

        override fun decode(data: ShortArray, start: kotlin.Int, end: kotlin.Int): String =
            String(CharArray(end - start) { i -> data[start + i].toInt().toChar() })
    }

    object UTF32 : Int {
        override val name = "utf32"
        override val alphabetSize: kotlin.Int = 0x110001
        override val separator: kotlin.Int = 0x110000
        override fun encode(text: String): IntArray = text.codePoints().toArray().also {
            it.any { i -> i == 0 } && throw IllegalArgumentException("文本包含 NUL，字节 0x00 被保留为终结符")
        }

        override fun decode(data: IntArray, start: kotlin.Int, end: kotlin.Int): String =
            String(data, start, end - start)
    }
}
