package minecraftx.text

import minecraftx.compose.text.McStyledString
import minecraftx.compose.text.TextWrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextWrapTest {

    private fun wrap(text: String, max: Int, single: Boolean = false) =
        TextWrap.layout(McStyledString(text), max, single, lineHeight = 10) { _, _ -> 1 }

    @Test
    fun `latin wraps at spaces`() {
        val layout = wrap("aa bb cc", 5)
        assertEquals(2, layout.lines.size)
        assertEquals("aa bb", layout.lines[0].runs.joinToString("") { it.text })
        assertEquals("cc", layout.lines[1].runs.joinToString("") { it.text })
    }

    @Test
    fun `cjk breaks every character`() {
        val layout = wrap("中文测试", 2)
        assertEquals(2, layout.lines.size)
        assertEquals("中文", layout.lines[0].runs.joinToString("") { it.text })
        assertEquals("测试", layout.lines[1].runs.joinToString("") { it.text })
    }

    @Test
    fun `hard-breaks an unbreakable word`() {
        val layout = wrap("abcdef", 3)
        assertEquals(2, layout.lines.size)
        assertEquals("abc", layout.lines[0].runs.joinToString("") { it.text })
        assertEquals("def", layout.lines[1].runs.joinToString("") { it.text })
    }

    @Test
    fun `single line truncates`() {
        val layout = wrap("abcdef", 3, single = true)
        assertEquals(1, layout.lines.size)
        assertEquals("abc", layout.lines[0].runs.joinToString("") { it.text })
    }

    @Test
    fun `hard newlines split lines`() {
        val layout = wrap("aa\nbb", 100)
        assertEquals(2, layout.lines.size)
        assertEquals("aa", layout.lines[0].runs.joinToString("") { it.text })
        assertEquals("bb", layout.lines[1].runs.joinToString("") { it.text })
    }

    @Test
    fun `empty input is one empty line`() {
        val layout = wrap("", 10)
        assertEquals(1, layout.lines.size)
        assertTrue(layout.lines[0].runs.isEmpty())
    }
}
