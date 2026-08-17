package allyouneed.compose.platform

import allyouneed.client.compose.platform.McTextInputService
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.MoveCursorCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the raw-key → [EditCommand] translation in [McTextInputService] with no Minecraft
 * dependencies: the service only depends on compose ui-text, so it runs on a plain JVM.
 *
 * The service's job is the key-to-command mapping, so the tests assert on the emitted commands
 * (cheap, hermetic). Where a command's `applyTo` does not depend on the skiko-native character
 * break iterator (commit / selection / delete), the buffer effect is additionally verified by
 * applying it through the official [EditProcessor].
 *
 * Key codes / modifiers below mirror the GLFW ABI values that Minecraft forwards through
 * `Screen.keyPressed` (declared privately inside [McTextInputService], re-declared here as literals).
 */
class McTextInputServiceTest {

    private val service = McTextInputService()
    private val commands = mutableListOf<EditCommand>()
    private var current = TextFieldValue("")
    private var action: ImeAction? = null

    private fun last(): EditCommand {
        assertTrue(commands.isNotEmpty(), "expected an emitted EditCommand")
        return commands.last()
    }

    /** Applies the captured commands through [EditProcessor] to keep `current` in sync. */
    private fun commitCaptured() {
        if (commands.isEmpty()) return
        val processor = EditProcessor()
        processor.reset(current, null)
        current = processor.apply(commands)
        commands.clear()
    }

    private fun register(
        imeEnabled: Boolean = true,
        singleLine: Boolean = true,
        initial: TextFieldValue = TextFieldValue(""),
    ) {
        commands.clear()
        current = initial
        action = null
        service.registerSession(
            id = 7,
            imeEnabled = imeEnabled,
            singleLine = singleLine,
            valueProvider = { current },
            onEditCommand = { commands += it },
            onImeActionPerformed = { action = it },
        )
    }

    // ---- ASCII mode (imeEnabled = false) ------------------------------------------------

    @Test
    fun `ascii mode maps printable keys through the US shift table`() {
        register(imeEnabled = false)
        assertTrue(service.onKeyPressed(65, 0)) // KEY_A
        assertEquals(CommitTextCommand("a", 1), last())
        assertTrue(service.onKeyPressed(65, 1)) // KEY_A + SHIFT
        assertEquals(CommitTextCommand("A", 1), last())
        assertTrue(service.onKeyPressed(49, 1)) // KEY_1 + SHIFT -> '!'
        assertEquals(CommitTextCommand("!", 1), last())
        assertTrue(service.onKeyPressed(61, 1)) // KEY_EQUAL + SHIFT -> '+'
        assertEquals(CommitTextCommand("+", 1), last())
        assertTrue(service.onKeyPressed(32, 0)) // KEY_SPACE
        assertEquals(CommitTextCommand(" ", 1), last())
        commitCaptured()
        assertEquals("aA!+ ", current.text)
    }

    @Test
    fun `ascii mode swallows committed characters`() {
        register(imeEnabled = false)
        assertTrue(service.onCharTyped('a'.code, 0))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `non-printable keys are not consumed without an active field`() {
        register(imeEnabled = false)
        service.unregisterSession(7)
        assertFalse(service.onKeyPressed(65, 0))
        assertFalse(service.onKeyPressed(256, 0)) // KEY_ESCAPE
    }

    // ---- IME mode (imeEnabled = true) ---------------------------------------------------

    @Test
    fun `ime mode inserts committed text via onCharTyped`() {
        register(imeEnabled = true)
        assertTrue(service.onCharTyped('你'.code, 0))
        assertTrue(service.onCharTyped('好'.code, 0))
        assertEquals(listOf(CommitTextCommand("你", 1), CommitTextCommand("好", 1)), commands)
        commitCaptured()
        assertEquals("你好", current.text)
    }

    @Test
    fun `ime mode consumes printable keys but defers insertion to onCharTyped`() {
        register(imeEnabled = true)
        assertTrue(service.onKeyPressed(65, 0)) // KEY_A consumed...
        assertTrue(commands.isEmpty()) // ...but not inserted yet
        assertTrue(service.onCharTyped('a'.code, 0))
        assertEquals(CommitTextCommand("a", 1), last())
    }

    @Test
    fun `non-printable keys fall through to vanilla in ime mode`() {
        register(imeEnabled = true)
        assertFalse(service.onKeyPressed(256, 0)) // KEY_ESCAPE
    }

    // ---- Editing keys (both modes) -----------------------------------------------------

    @Test
    fun `collapsed backspace selects the preceding grapheme then deletes via the pure selection branch`() {
        register(imeEnabled = false, initial = TextFieldValue("abc", TextRange(3)))
        assertTrue(service.onKeyPressed(259, 0)) // KEY_BACKSPACE
        // Contract that keeps us off skiko: never run BackspaceCommand on a collapsed cursor —
        // a SetSelectionCommand first turns it into the pure-JVM delete-selection branch.
        assertEquals(listOf(SetSelectionCommand(2, 3), BackspaceCommand()), commands)
        commitCaptured()
        assertEquals("ab", current.text)
        assertEquals(2, current.selection.min)
    }

    @Test
    fun `collapsed backspace deletes a whole surrogate pair`() {
        register(imeEnabled = false, initial = TextFieldValue("A😀B", TextRange(3)))
        assertTrue(service.onKeyPressed(259, 0))
        assertEquals(listOf(SetSelectionCommand(1, 3), BackspaceCommand()), commands)
        commitCaptured()
        assertEquals("AB", current.text)
        assertEquals(1, current.selection.min)
    }

    @Test
    fun `backspace with an active selection just deletes it`() {
        register(imeEnabled = false, initial = TextFieldValue("hello", TextRange(1, 4)))
        assertTrue(service.onKeyPressed(259, 0)) // KEY_BACKSPACE
        assertEquals(listOf(BackspaceCommand()), commands)
        commitCaptured()
        assertEquals("ho", current.text)
    }

    @Test
    fun `delete removes the character after the cursor`() {
        register(imeEnabled = false, initial = TextFieldValue("ab", TextRange(1)))
        assertTrue(service.onKeyPressed(261, 0)) // KEY_DELETE
        assertEquals(DeleteSurroundingTextCommand(0, 1), last())
        commitCaptured()
        assertEquals("a", current.text)
    }

    @Test
    fun `arrow keys move the cursor by one grapheme`() {
        register(imeEnabled = false, initial = TextFieldValue("abc", TextRange(1)))
        assertTrue(service.onKeyPressed(263, 0)) // KEY_LEFT
        assertEquals(SetSelectionCommand(0, 0), last())
        assertTrue(service.onKeyPressed(262, 0)) // KEY_RIGHT
        assertEquals(SetSelectionCommand(2, 2), last())
    }

    @Test
    fun `arrow keys with a selection collapse to the near edge`() {
        register(imeEnabled = false, initial = TextFieldValue("abcde", TextRange(1, 4)))
        assertTrue(service.onKeyPressed(263, 0)) // KEY_LEFT from selection -> collapses to start
        assertEquals(SetSelectionCommand(0, 0), last())
        assertTrue(service.onKeyPressed(262, 0)) // KEY_RIGHT -> collapses to end
        assertEquals(SetSelectionCommand(5, 5), last())
    }

    @Test
    fun `home and end jump to the edges`() {
        register(imeEnabled = false, initial = TextFieldValue("abc", TextRange(2)))
        assertTrue(service.onKeyPressed(268, 0)) // KEY_HOME
        assertEquals(SetSelectionCommand(0, 0), last())
        assertTrue(service.onKeyPressed(269, 0)) // KEY_END
        assertEquals(SetSelectionCommand(Int.MAX_VALUE, Int.MAX_VALUE), last())
        commitCaptured()
        assertEquals(3, current.selection.max)
    }

    @Test
    fun `shift arrows extend the selection from its anchor`() {
        register(imeEnabled = false, initial = TextFieldValue("abcdef", TextRange(2)))
        assertTrue(service.onKeyPressed(263, 1)) // SHIFT + KEY_LEFT
        commitCaptured()
        assertEquals(1, current.selection.min)
        assertEquals(2, current.selection.max)
        assertTrue(service.onKeyPressed(263, 1))
        commitCaptured()
        assertEquals(0, current.selection.min)
        assertEquals(2, current.selection.max)
        assertTrue(service.onKeyPressed(262, 1)) // SHIFT + KEY_RIGHT
        commitCaptured()
        assertEquals(0, current.selection.min)
        assertEquals(3, current.selection.max)
    }

    @Test
    fun `ctrl+a selects everything`() {
        register(imeEnabled = false, initial = TextFieldValue("hello"))
        assertTrue(service.onKeyPressed(65, 2)) // KEY_A + CTRL
        assertEquals(SetSelectionCommand(0, Int.MAX_VALUE), last())
        commitCaptured()
        assertEquals(0, current.selection.min)
        assertEquals(5, current.selection.max)
    }

    @Test
    fun `ctrl+c copies the selection when a clipboard is attached`() {
        val clipboard = FakeClipboard()
        service.clipboard = clipboard
        register(imeEnabled = false, initial = TextFieldValue("hello", TextRange(1, 4)))
        assertTrue(service.onKeyPressed(67, 2)) // KEY_C + CTRL
        assertEquals("ell", clipboard.stored)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `ctrl+x cuts the selection`() {
        val clipboard = FakeClipboard()
        service.clipboard = clipboard
        register(imeEnabled = false, initial = TextFieldValue("hello", TextRange(1, 4)))
        assertTrue(service.onKeyPressed(88, 2)) // KEY_X + CTRL
        assertEquals("ell", clipboard.stored)
        assertEquals(BackspaceCommand(), last())
    }

    @Test
    fun `ctrl+v pastes clipboard text`() {
        val clipboard = FakeClipboard("xy")
        service.clipboard = clipboard
        register(imeEnabled = false, initial = TextFieldValue("a", TextRange(1)))
        assertTrue(service.onKeyPressed(86, 2)) // KEY_V + CTRL
        assertEquals(CommitTextCommand("xy", 1), last())
    }

    @Test
    fun `clipboard shortcuts fall through when no clipboard is attached`() {
        service.clipboard = null
        register(imeEnabled = false, initial = TextFieldValue("hello", TextRange(0, 5)))
        assertFalse(service.onKeyPressed(67, 2))
        assertFalse(service.onKeyPressed(88, 2))
        assertFalse(service.onKeyPressed(86, 2))
    }

    private class FakeClipboard(var stored: String? = null) : allyouneed.client.compose.platform.TextClipboard {
        override fun getText(): String? = stored
        override fun setText(text: String) {
            stored = text
        }
    }

    @Test
    fun `enter fires the ime action on a single-line field`() {
        register(imeEnabled = false, singleLine = true, initial = TextFieldValue("x", TextRange(1)))
        assertTrue(service.onKeyPressed(257, 0)) // KEY_ENTER
        assertEquals(ImeAction.Done, action)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `enter inserts a newline on a multi-line field`() {
        register(imeEnabled = false, singleLine = false, initial = TextFieldValue("x", TextRange(1)))
        assertTrue(service.onKeyPressed(257, 0))
        assertEquals(CommitTextCommand("\n", 1), last())
    }

    // ---- Session lifecycle -------------------------------------------------------------

    @Test
    fun `re-registering another field supersedes the previous one`() {
        register(imeEnabled = false)
        service.registerSession(
            id = 8,
            imeEnabled = false,
            singleLine = true,
            valueProvider = { current },
            onEditCommand = { commands += it },
            onImeActionPerformed = { action = it },
        )
        assertTrue(service.onKeyPressed(65, 0))
        assertEquals("a", (last() as CommitTextCommand).text)
        // The superseded field unregistering must not clear the active session.
        service.unregisterSession(7)
        commands.clear()
        assertTrue(service.onKeyPressed(65, 0))
        assertEquals("a", (last() as CommitTextCommand).text)
    }
}
