package allyouneed.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.TranslatableContents
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComponentsTest {
    @Test
    fun `semantic blocks apply theme styles and preserve content order`() {
        val theme = ComponentTheme(base = Style.EMPTY.withUnderlined(true))
        val result = Components.build(theme) {
            title { line("title") }
            description { l10nLine("test.components.description") }
            usage { line("usage") }
            feature { line("feature") }
            enhancement { line("enhancement") }
            requirement { line("requirement") }
            rule { line("rule") }
            status { line("status") }
            hint { line("hint") }
            detail { line("detail") }
            warning { text("warning") }
            text("plain")
        }

        assertEquals(11, result.size)
        assertEquals("test.components.description", assertIs<TranslatableContents>(result[1].contents).key)
        assertEquals(
            listOf("title", "usage", "feature", "enhancement", "requirement", "rule", "status", "hint", "detail", "warningplain"),
            result.filterIndexed { index, _ -> index != 1 }.map { it.string },
        )
        val styles = listOf(
            theme.title, theme.description, theme.usage, theme.feature, theme.enhancement,
            theme.requirement, theme.rule, theme.status, theme.hint, theme.detail,
        )
        styles.forEachIndexed { index, style -> assertEquals(style.applyTo(theme.base), result[index].style) }
        assertEquals(theme.warning.applyTo(theme.base), result.last().siblings[0].style)
        assertEquals(theme.base, result.last().siblings[1].style)
    }

    @Test
    fun `status levels merge with status defaults and restore enclosing styles`() {
        val theme = ComponentTheme(
            base = Style.EMPTY.withUnderlined(true),
            status = Style.EMPTY.withColor(0x123456).withItalic(true).withBold(true),
            statusAttention = Style.EMPTY.withColor(0xABCDEF).withBold(false),
            statusError = Style.EMPTY.withColor(0x654321).withItalic(false),
        )
        val result = Components.build(theme) {
            status {
                text("normal")
                status(ComponentStatusLevel.ATTENTION) { text("attention") }
                text("restored")
                try {
                    status(ComponentStatusLevel.ERROR) {
                        text("error")
                        error("expected")
                    }
                } catch (_: IllegalStateException) {
                    text("recovered")
                }
            }
            text("base")
            status(ComponentStatusLevel.ERROR) { text("override").green.italic(true) }
        }

        val fragments = result.single().siblings
        assertEquals(listOf("normal", "attention", "restored", "error", "recovered", "base", "override"), fragments.map { it.string })
        for (index in listOf(0, 2, 4)) {
            assertEquals(theme.status.applyTo(theme.base), fragments[index].style)
        }
        assertEquals(theme.statusAttention.applyTo(theme.status).applyTo(theme.base), fragments[1].style)
        assertFalse(fragments[1].style.isBold)
        assertEquals(theme.statusError.applyTo(theme.status).applyTo(theme.base), fragments[3].style)
        assertFalse(fragments[3].style.isItalic)
        assertEquals(theme.base, fragments[5].style)
        assertEquals(ChatFormatting.GREEN.color, fragments[6].style.color?.value)
        assertTrue(fragments[6].style.isItalic)
        assertTrue(fragments.all { it.style.isUnderlined })
    }

    @Test
    fun `custom theme supports nesting explicit overrides and exception recovery`() {
        val theme = ComponentTheme(
            base = Style.EMPTY.withItalic(true),
            title = Style.EMPTY.withColor(0x123456).withBold(true),
            warning = Style.EMPTY.withColor(0xABCDEF).withBold(false),
        )
        val source = Component.literal("source").withStyle(ChatFormatting.GREEN)
        val result = Components.build(theme) {
            title {
                try {
                    warning {
                        line("warning")
                        error("expected")
                    }
                } catch (_: IllegalStateException) {
                    line("title")
                }
                line(source)
                red { line("local") }
                line("editor").blue.bold(false)
            }
            line("base")
        }

        assertEquals(theme.warning.applyTo(theme.title).applyTo(theme.base), result[0].style)
        assertEquals(theme.title.applyTo(theme.base), result[1].style)
        assertEquals(ChatFormatting.GREEN.color, result[2].style.color?.value)
        assertEquals(ChatFormatting.RED.color, result[3].style.color?.value)
        assertEquals(ChatFormatting.BLUE.color, result[4].style.color?.value)
        assertFalse(result[4].style.isBold)
        assertTrue(result.all { it.style.isItalic })
        assertEquals(theme.base, result[5].style)
        assertFalse(source.style.isBold)
        assertFalse(source.style.isItalic)
    }

    @Test
    fun `global theme is captured per build and explicit themes remain local`() {
        val previous = Components.defaultTheme
        val first = ComponentTheme(title = Style.EMPTY.withColor(0x123456))
        val second = first.copy(title = Style.EMPTY.withColor(0xABCDEF))
        try {
            Components.defaultTheme = first
            val captured = Components.build {
                title { line("before") }
                Components.defaultTheme = second
                title { line("after") }
            }
            assertTrue(captured.all { it.style == first.title })
            assertEquals(second.title, Components.build { title { line("new") } }.single().style)
            assertEquals(first.title, Components.build(first) { title { line("local") } }.single().style)
            assertSame(second, Components.defaultTheme)
        } finally {
            Components.defaultTheme = previous
        }
    }

    @Test
    fun `empty builder produces no lines`() {
        assertTrue(Components.build {}.isEmpty())
    }

    @Test
    fun `text fragments form one final line`() {
        val result = Components.build {
            text("Hello")
            text(" ")
            text("世界")
        }

        assertEquals(listOf("Hello 世界"), result.map { it.string })
    }

    @Test
    fun `lines flush pending text and preserve order`() {
        val result = Components.build {
            text("first")
            text(" part")
            line("second")
            line("third")
            text("fourth")
            newLine
            line("fifth")
        }

        assertEquals(listOf("first part", "second", "third", "fourth", "fifth"), result.map { it.string })
    }

    @Test
    fun `explicit newlines preserve blank lines`() {
        val result = Components.build {
            newLine
            text("middle")
            newLine
            newLine
            line("last")
            newLine
        }

        assertEquals(listOf("", "middle", "", "last", ""), result.map { it.string })
    }

    @Test
    fun `ending text with newline does not add another line`() {
        val result = Components.build {
            text("content")
            newLine
        }

        assertEquals(listOf("content"), result.map { it.string })
    }

    @Test
    fun `empty text and empty line are retained`() {
        val result = Components.build {
            text("")
            line("")
            text("")
        }

        assertEquals(listOf("", "", ""), result.map { it.string })
    }

    @Test
    fun `component factories do not append implicitly`() {
        val result = Components.build {
            assertEquals("literal", str("literal").string)
            val translation = assertIs<TranslatableContents>(l10n("test.components.unused").contents)
            assertEquals("test.components.unused", translation.key)
            assertTrue(translation.args.isEmpty())
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `localized lines and fragments retain keys and arguments`() {
        val argument = Component.literal("argument")
        val result = Components.build {
            l10nText("test.components.fragment", argument, 42, null)
            l10nLine("test.components.line", "value")
            line(l10n("test.components.factory"))
        }

        assertEquals(3, result.size)
        val fragment = assertIs<TranslatableContents>(result[0].siblings.single().contents)
        assertEquals("test.components.fragment", fragment.key)
        assertEquals(listOf(argument, 42, null), fragment.args.toList())
        val line = assertIs<TranslatableContents>(result[1].contents)
        assertEquals("test.components.line", line.key)
        assertEquals(listOf("value"), line.args.toList())
        val factory = assertIs<TranslatableContents>(result[2].contents)
        assertEquals("test.components.factory", factory.key)
        assertTrue(factory.args.isEmpty())
    }

    @Test
    fun `styling copied components leaves source unchanged`() {
        val source = Component.literal("source").withStyle(ChatFormatting.GREEN)
            .append(Component.literal(" child"))
        val result = Components.build {
            line(source).red.bold
            text(source).blue.italic
        }

        val line = result[0]
        val fragment = result[1].siblings.single()
        assertNotSame(source, line)
        assertNotSame(source, fragment)
        assertEquals(ChatFormatting.GREEN.color, source.style.color?.value)
        assertFalse(source.style.isBold)
        assertFalse(source.style.isItalic)
        assertEquals(ChatFormatting.RED.color, line.style.color?.value)
        assertTrue(line.style.isBold)
        assertEquals(ChatFormatting.BLUE.color, fragment.style.color?.value)
        assertTrue(fragment.style.isItalic)

        source.append(" later")
        assertEquals(listOf("source child", "source child"), result.map { it.string })
    }

    @Test
    fun `chained formatting applies only to its fragment`() {
        val result = Components.build {
            val editor = text("styled")
            assertSame(editor, editor.red.bold.italic.underline.strikethrough.obfuscated)
            text("plain")
        }

        val line = result.single()
        val style = line.siblings[0].style
        assertEquals(ChatFormatting.RED.color, style.color?.value)
        assertTrue(style.isBold)
        assertTrue(style.isItalic)
        assertTrue(style.isUnderlined)
        assertTrue(style.isStrikethrough)
        assertTrue(style.isObfuscated)
        assertTrue(line.style.isEmpty)
        assertTrue(line.siblings[1].style.isEmpty)
        assertEquals("styledplain", line.string)
    }

    @Test
    fun `all color editors set the expected color`() {
        val colors: List<Pair<ChatFormatting, MutableComponentEditor.() -> MutableComponentEditor>> = listOf(
            ChatFormatting.BLACK to { black },
            ChatFormatting.DARK_BLUE to { darkBlue },
            ChatFormatting.DARK_GREEN to { darkGreen },
            ChatFormatting.DARK_AQUA to { darkAqua },
            ChatFormatting.DARK_RED to { darkRed },
            ChatFormatting.DARK_PURPLE to { darkPurple },
            ChatFormatting.GOLD to { gold },
            ChatFormatting.GRAY to { gray },
            ChatFormatting.DARK_GRAY to { darkGray },
            ChatFormatting.BLUE to { blue },
            ChatFormatting.GREEN to { green },
            ChatFormatting.AQUA to { aqua },
            ChatFormatting.RED to { red },
            ChatFormatting.LIGHT_PURPLE to { lightPurple },
            ChatFormatting.YELLOW to { yellow },
            ChatFormatting.WHITE to { white },
        )

        for ((color, applyColor) in colors) {
            val result = Components.build { line("color").applyColor() }
            assertEquals(color.color, result.single().style.color?.value, color.name)
        }
    }

    @Test
    fun `last color wins without clearing decorations`() {
        val result = Components.build { line("styled").red.bold.blue }

        assertEquals(ChatFormatting.BLUE.color, result.single().style.color?.value)
        assertTrue(result.single().style.isBold)
    }

    @Test
    fun `build calls have independent state`() {
        val first = Components.build { text("first").red }
        val second = Components.build { line("second") }

        assertEquals(listOf("first"), first.map { it.string })
        assertEquals(listOf("second"), second.map { it.string })
        assertTrue(second.single().style.isEmpty)
        assertTrue(Components.build {}.isEmpty())
    }

    @Test
    fun `nested style blocks restore outer defaults without splitting lines`() {
        val result = Components.build {
            bold {
                text("bold")
                red {
                    italic { text("nested") }
                    text("red")
                }
                text("outer")
            }
            text("plain")
        }

        val fragments = result.single().siblings
        assertEquals(listOf("bold", "nested", "red", "outer", "plain"), fragments.map { it.string })
        assertTrue(fragments.take(4).all { it.style.isBold })
        assertTrue(fragments[1].style.isItalic)
        assertFalse(fragments[2].style.isItalic)
        assertEquals(ChatFormatting.RED.color, fragments[1].style.color?.value)
        assertEquals(ChatFormatting.RED.color, fragments[2].style.color?.value)
        assertEquals(null, fragments[3].style.color)
        assertTrue(fragments[4].style.isEmpty)
    }

    @Test
    fun `blocks apply to lines translations and fragments without changing line order`() {
        val result = Components.build {
            text("before")
            color(0x66CCFF) {
                line("line")
                l10nLine("test.components.line")
                l10nText("test.components.fragment")
                newLine
                newLine
            }
            line("after")
        }

        assertEquals(6, result.size)
        assertEquals("before", result[0].string)
        assertEquals(0x66CCFF, result[1].style.color?.value)
        assertEquals(0x66CCFF, result[2].style.color?.value)
        assertEquals(0x66CCFF, result[3].siblings.single().style.color?.value)
        assertEquals("", result[4].string)
        assertEquals("after", result[5].string)
        assertTrue(result[5].style.isEmpty)
    }

    @Test
    fun `explicit component styles and editor overrides take precedence over defaults`() {
        val source = Component.literal("source").withStyle { it.withColor(0x123456).withBold(false) }
        val result = Components.build {
            red {
                bold {
                    line(source)
                    line("override").color(0xABCDEF).bold(false).italic(true)
                    line("merged").style(Style.EMPTY.withColor(0x654321).withUnderlined(true))
                }
            }
        }

        assertEquals(0x123456, result[0].style.color?.value)
        assertFalse(result[0].style.isBold)
        assertNotSame(source, result[0])
        assertEquals(0x123456, source.style.color?.value)
        assertEquals(0xABCDEF, result[1].style.color?.value)
        assertFalse(result[1].style.isBold)
        assertTrue(result[1].style.isItalic)
        assertEquals(0x654321, result[2].style.color?.value)
        assertTrue(result[2].style.isBold)
        assertTrue(result[2].style.isUnderlined)
    }

    @Test
    fun `decoration blocks support disabling and editor overrides`() {
        val decorated = Style.EMPTY.withBold(true).withItalic(true).withUnderlined(true)
            .withStrikethrough(true).withObfuscated(true)
        val result = Components.build {
            style(decorated) {
                bold(false) {
                    italic(false) {
                        underline(false) {
                            strikethrough(false) {
                                obfuscated(false) { line("disabled") }
                            }
                        }
                    }
                }
                line("restored")
                line("editor").bold(false).italic(false).underline(false).strikethrough(false).obfuscated(false)
            }
            italic {
                underline {
                    strikethrough {
                        obfuscated { line("enabled") }
                    }
                }
            }
        }

        for (index in listOf(0, 2)) {
            val style = result[index].style
            assertFalse(style.isBold)
            assertFalse(style.isItalic)
            assertFalse(style.isUnderlined)
            assertFalse(style.isStrikethrough)
            assertFalse(style.isObfuscated)
        }
        assertEquals(decorated, result[1].style)
        assertEquals(decorated.withBold(null), result[3].style)
    }

    @Test
    fun `style defaults are restored after a block throws`() {
        val result = Components.build {
            bold {
                try {
                    red {
                        text("before failure")
                        error("expected")
                    }
                } catch (_: IllegalStateException) {
                    text("recovered")
                }
            }
            text("plain")
        }

        val fragments = result.single().siblings
        assertEquals(ChatFormatting.RED.color, fragments[0].style.color?.value)
        assertTrue(fragments[1].style.isBold)
        assertEquals(null, fragments[1].style.color)
        assertTrue(fragments[2].style.isEmpty)
    }

    @Test
    fun `batch lines flush pending text and copy components with scoped defaults`() {
        val source = Component.literal("source")
        val result = Components.build {
            text("pending")
            lines(emptyList())
            text(" text")
            bold { lines(listOf(source, Component.literal("second"))) }
            line("last")
        }

        assertEquals(listOf("pending text", "source", "second", "last"), result.map { it.string })
        assertNotSame(source, result[1])
        assertTrue(source.style.isEmpty)
        assertTrue(result[1].style.isBold)
        assertTrue(result[2].style.isBold)
        assertTrue(result[3].style.isEmpty)
    }
}
