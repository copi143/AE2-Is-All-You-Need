package minecraftx.markdown

import minecraftx.compose.markdown.MdParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdParserTest {

    @Test
    fun `inline code span is not empty`() {
        val blocks = MdParser.parse("use `inline code` here")
        val text = blocks.joinToString("") { block ->
            val styled = (block as? minecraftx.compose.markdown.MdBlock.Paragraph)?.styled
            styled?.text.orEmpty()
        }
        assertTrue(text.contains("inline code"), text)
    }
}
