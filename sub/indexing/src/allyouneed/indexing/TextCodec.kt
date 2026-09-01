package allyouneed.indexing

/**
 * 文本编码器：把 [String] 转换为符号序列（[IntArray]），并定义「文档分隔符」。
 *
 * 三种实现分别以 8/16/32 位为单元，决定中文字符、emoji 等多字节内容的匹配粒度：
 * - [Utf8Codec]：以 UTF-8 字节为单元，内存最小、最快，适合海量 ASCII/拉丁文本；
 * - [Utf16Codec]：以 UTF-16 char 为单元，与 JVM [String] 的 char 语义一致（代理对会被拆开）；
 * - [Utf32Codec]：以 Unicode 码点为单元，完整字符级匹配。
 *
 * [separator] 是编码后符号取值域之外的一个哨兵值（恒等于最大符号 + 1），
 * 由 [FMIndex] 用作文档前后边界，实现前缀/后缀匹配。
 */
interface TextCodec {
    val name: String

    /** 分隔符符号，恒等于最大有效符号 + 1。 */
    val separator: Int

    /** 编码后符号取值范围为 `[0, separator)`。 */
    fun encode(text: String): IntArray
}

object Utf8Codec : TextCodec {
    override val name = "utf8"
    override val separator = 256

    override fun encode(text: String): IntArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
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
