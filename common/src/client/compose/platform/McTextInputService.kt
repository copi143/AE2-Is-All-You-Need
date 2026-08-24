@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package allyouneed.client.compose.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import java.text.BreakIterator

/**
 * The framework's text-input bridge between Minecraft's key events and the active [McTextField].
 *
 * Minecraft has no IME preedit API (the GLFW char callback only reports **committed** text), so the
 * service implements the same "committed-text" level of IME support as the vanilla [net.minecraft.client.gui.components.EditBox]:
 *
 *  - **IME on** ([imeEnabled]): text arrives through `onCharTyped` — direct keys and committed IME
 *    text alike — while `onKeyPressed` only handles editing keys (backspace, arrows, ...) and
 *    consumes printable keys so the host screen never sees them.
 *  - **IME off** (ASCII): `onCharTyped` is ignored and printable keys are translated through a
 *    US-layout ASCII map (`keyCode` + shift) in `onKeyPressed`.
 *
 * Raw key events are forwarded by the host screen (`ComposeScreen` / `ComposeContainerScreen`) via
 * [ComposeLayer.onKeyPressed] / [ComposeLayer.onCharTyped]. The service turns them into compose
 * [EditCommand]s and routes them to the single active field's callback — exactly the same commands
 * the OS IME would produce through [PlatformTextInputService.startInput], so [McTextField] can reuse
 * the official [androidx.compose.ui.text.input.EditProcessor] for its buffer state.
 *
 * **Skiko 边界**：官方桌面 jar 的 `findPrecedingBreak`/`findFollowingBreak`（[androidx.compose.ui.text.input.BackspaceCommand]
 * 折叠光标分支、[androidx.compose.ui.text.input.MoveCursorCommand]）依赖 `org.jetbrains.skia.BreakIterator`，
 * 而框架不加载 skiko native。因此这里从不应用折叠光标的 [androidx.compose.ui.text.input.BackspaceCommand]
 * ——折叠时先发 `SetSelectionCommand(prev, cursor)` 造出选区，让 BackspaceCommand 走纯 JVM 的删选区分支；
 * 方向键移动也直接算好目标后用 `SetSelectionCommand` 表达，绝不发射 [androidx.compose.ui.text.input.MoveCursorCommand]。
 * 字符边界统一用 `java.text.BreakIterator.getCharacterInstance()` 计算（纯 JDK，无 skiko 依赖）。
 *
 * This file intentionally has **no Minecraft imports**: it only depends on compose ui-text, which
 * lets it run under a plain JVM unit test (see `common/test/.../McTextInputServiceTest.kt`).
 *
 * Keyboard codes mirror the GLFW ABI values ([org.lwjgl.glfw.GLFW] constants that Minecraft forwards
 * through `Screen.keyPressed`); they are re-declared here as plain Ints to keep the lwjgl dependency
 * out of the common module.
 */
interface TextClipboard {
    fun getText(): String?
    fun setText(text: String)
}

/**
 * Optional per-session hook for multi-line fields ([McTextArea]): vertical caret movement and
 * line-boundary jumps are *visual* concepts — with soft wrapping only the field itself knows which
 * row an offset belongs to — so the service delegates those keys to the active field instead of
 * computing them from the raw text.
 */
interface TextNavigation {
    /** Moves the caret [deltaRows] visual rows down (positive) or up; extends the selection on shift. */
    fun moveVertically(deltaRows: Int, select: Boolean)

    /** Moves to the start / end of the current visual row; extends the selection on shift. */
    fun toLineBoundary(toStart: Boolean, select: Boolean)
}

class McTextInputService : PlatformTextInputService {

    var clipboard: TextClipboard? = null

    private var onEditCommand: ((List<EditCommand>) -> Unit)? = null
    private var onImeActionPerformed: ((ImeAction) -> Unit)? = null
    private var valueProvider: () -> TextFieldValue = { TextFieldValue("") }
    private var singleLine = true
    private var navigation: TextNavigation? = null

    /** Snapshot state: id of the currently-active text field; recomposes fields on focus change. */
    var activeSession: Int by mutableIntStateOf(NO_SESSION)
        private set

    /** Whether the active field accepts IME text (`onCharTyped`) instead of ASCII-only keys. */
    var imeEnabled: Boolean by mutableStateOf(true)
        private set

    /** True while some field is registered as the active input session. */
    val hasActiveSession: Boolean get() = onEditCommand != null

    /**
     * Makes the field with [id] the active input session. Any previously-registered field is
     * implicitly replaced (its own `LaunchedEffect` will then call [unregisterSession], a no-op
     * because [activeSession] already moved on).
     */
    fun registerSession(
        id: Int,
        imeEnabled: Boolean,
        singleLine: Boolean,
        valueProvider: () -> TextFieldValue,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
        navigation: TextNavigation? = null,
    ) {
        activeSession = id
        this.imeEnabled = imeEnabled
        this.singleLine = singleLine
        this.valueProvider = valueProvider
        this.onEditCommand = onEditCommand
        this.onImeActionPerformed = onImeActionPerformed
        this.navigation = navigation
    }

    /** Releases the session only if [id] is still the active field. */
    fun unregisterSession(id: Int) {
        if (activeSession != id) return
        activeSession = NO_SESSION
        onEditCommand = null
        onImeActionPerformed = null
        navigation = null
    }

    /** Requests focus for the field with [id] without touching its command callbacks yet. */
    fun activate(id: Int) {
        activeSession = id
    }

    /**
     * Handles a raw GLFW [keyCode] + [modifiers] key-press. Returns true when the event was consumed
     * by the active field (vanilla must not see it). See class doc for the ime on/off split.
     */
    fun onKeyPressed(keyCode: Int, modifiers: Int): Boolean {
        val onEditCommand = onEditCommand ?: return false
        val ctrl = modifiers and MOD_CONTROL != 0
        val shift = modifiers and MOD_SHIFT != 0
        when (keyCode) {
            KEY_BACKSPACE -> {
                val selection = valueProvider().selection
                if (selection.min < selection.max) {
                    // Selection active: the official command deletes it without the break iterator.
                    onEditCommand(listOf(BackspaceCommand()))
                } else if (selection.min > 0) {
                    // Collapsed cursor: select the preceding grapheme first, so BackspaceCommand
                    // takes the pure-JVM delete-selection branch instead of skiko's BreakIterator.
                    val prev = prevBreak(valueProvider().text, selection.min)
                    onEditCommand(listOf(SetSelectionCommand(prev, selection.min), BackspaceCommand()))
                } else {
                    onEditCommand(emptyList())
                }
            }
            KEY_DELETE -> onEditCommand(listOf(DeleteSurroundingTextCommand(0, 1)))
            KEY_LEFT -> if (shift) extendSelection(-1) else moveCursorBy(-1)
            KEY_RIGHT -> if (shift) extendSelection(1) else moveCursorBy(1)
            KEY_UP, KEY_DOWN -> {
                // Vertical movement needs the field's visual layout — without a navigation hook
                // (single-line fields) the key falls through to vanilla.
                val nav = navigation ?: return false
                nav.moveVertically(if (keyCode == KEY_UP) -1 else 1, shift)
            }
            KEY_HOME -> {
                val nav = navigation
                if (nav != null) nav.toLineBoundary(true, shift)
                else if (shift) extendSelectionToStart() else onEditCommand(listOf(SetSelectionCommand(0, 0)))
            }
            KEY_END -> {
                val nav = navigation
                if (nav != null) nav.toLineBoundary(false, shift)
                else if (shift) extendSelectionToEnd() else onEditCommand(listOf(SetSelectionCommand(Int.MAX_VALUE, Int.MAX_VALUE)))
            }
            KEY_ENTER, KEY_KP_ENTER -> {
                if (singleLine) {
                    onImeActionPerformed?.invoke(ImeAction.Done)
                } else {
                    onEditCommand(listOf(CommitTextCommand("\n", 1)))
                }
            }
            else -> {
                if (ctrl && keyCode == KEY_A) {
                    onEditCommand(listOf(SetSelectionCommand(0, Int.MAX_VALUE)))
                } else if (ctrl && keyCode == KEY_C) {
                    copySelection() ?: return false
                } else if (ctrl && keyCode == KEY_X) {
                    if (!cutSelection()) return false
                } else if (ctrl && keyCode == KEY_V) {
                    if (!pasteClipboard()) return false
                } else if (ctrl) {
                    return false
                } else if (asciiForKey(keyCode, shift) != null) {
                    // Printable key. In IME mode the actual character is delivered by onCharTyped
                    // (direct keys or committed IME text), so only consume it here; in ASCII mode
                    // translate it straight away.
                    if (!imeEnabled) onEditCommand(listOf(CommitTextCommand(asciiForKey(keyCode, shift).toString(), 1)))
                } else {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Handles a committed character (direct key press or IME commit). Returns true when consumed.
     * With the IME disabled the event is swallowed and ASCII typing goes through [onKeyPressed].
     */
    fun onCharTyped(codePoint: Int, modifiers: Int): Boolean {
        val onEditCommand = onEditCommand ?: return false
        if (!imeEnabled) return true
        if (!Character.isValidCodePoint(codePoint)) return false
        onEditCommand(listOf(CommitTextCommand(String(Character.toChars(codePoint)), 1)))
        return true
    }

    private fun extendSelection(direction: Int) {
        val onEditCommand = onEditCommand ?: return
        val value = valueProvider()
        if (direction < 0) {
            // Shift+Left: the right edge is the anchor; the active (left) edge steps one grapheme back.
            onEditCommand(listOf(SetSelectionCommand(prevBreak(value.text, value.selection.min), value.selection.max)))
        } else {
            // Shift+Right: the left edge is the anchor; the active (right) edge steps one grapheme forward.
            onEditCommand(listOf(SetSelectionCommand(value.selection.min, nextBreak(value.text, value.selection.max))))
        }
    }

    private fun extendSelectionToStart() {
        val onEditCommand = onEditCommand ?: return
        onEditCommand(listOf(SetSelectionCommand(0, valueProvider().selection.max)))
    }

    private fun extendSelectionToEnd() {
        val onEditCommand = onEditCommand ?: return
        onEditCommand(listOf(SetSelectionCommand(valueProvider().selection.min, Int.MAX_VALUE)))
    }

    private fun copySelection(): Unit? {
        val clipboard = clipboard ?: return null
        val value = valueProvider()
        val selected = value.text.substring(value.selection.min, value.selection.max)
        clipboard.setText(selected)
        return Unit
    }

    private fun cutSelection(): Boolean {
        val clipboard = clipboard ?: return false
        val onEditCommand = onEditCommand ?: return false
        val value = valueProvider()
        if (value.selection.min >= value.selection.max) return false
        clipboard.setText(value.text.substring(value.selection.min, value.selection.max))
        onEditCommand(listOf(BackspaceCommand()))
        return true
    }

    private fun pasteClipboard(): Boolean {
        val clipboard = clipboard ?: return false
        val onEditCommand = onEditCommand ?: return false
        val text = clipboard.getText() ?: return false
        if (text.isEmpty()) return true
        onEditCommand(listOf(CommitTextCommand(text, 1)))
        return true
    }

    private fun moveCursorBy(direction: Int) {
        val onEditCommand = onEditCommand ?: return
        val value = valueProvider()
        val selection = value.selection
        val base = if (direction < 0) selection.min else selection.max
        val target = if (direction < 0) prevBreak(value.text, base) else nextBreak(value.text, base)
        onEditCommand(listOf(SetSelectionCommand(target, target)))
    }

    private fun prevBreak(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        val boundary = BreakIterator.getCharacterInstance().apply { setText(text) }.preceding(offset)
        return if (boundary < 0) 0 else boundary
    }

    private fun nextBreak(text: String, offset: Int): Int {
        if (offset >= text.length) return text.length
        val boundary = BreakIterator.getCharacterInstance().apply { setText(text) }.following(offset)
        return if (boundary < 0) text.length else boundary
    }

    // PlatformTextInputService ---------------------------------------------------------------

    override fun startInput(
        value: TextFieldValue,
        imeOptions: androidx.compose.ui.text.input.ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
    ) {
        registerSession(
            id = activeSession.takeIf { it != NO_SESSION } ?: 0,
            imeEnabled = imeEnabled,
            singleLine = imeOptions.singleLine,
            valueProvider = { value },
            onEditCommand = onEditCommand,
            onImeActionPerformed = onImeActionPerformed,
        )
    }

    override fun stopInput() {
        onEditCommand = null
        onImeActionPerformed = null
        navigation = null
    }

    override fun showSoftwareKeyboard() {}

    override fun hideSoftwareKeyboard() {}

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {}

    // ASCII helpers -------------------------------------------------------------------------

    /** Maps a GLFW key code + shift to its US-layout character, or null for non-printable keys. */
    private fun asciiForKey(keyCode: Int, shift: Boolean): Char? {
        if (keyCode in KEY_A..KEY_Z) {
            val letter = (keyCode - KEY_A + 'a'.code).toChar()
            return if (shift) letter.uppercaseChar() else letter
        }
        if (keyCode in KEY_0..KEY_9) {
            return if (shift) SHIFTED_DIGITS[keyCode - KEY_0] else (keyCode - KEY_0 + '0'.code).toChar()
        }
        if (keyCode in KEY_KP_0..KEY_KP_9) return (keyCode - KEY_KP_0 + '0'.code).toChar()
        return when (keyCode) {
            KEY_SPACE -> ' '
            KEY_MINUS -> if (shift) '_' else '-'
            KEY_EQUAL -> if (shift) '+' else '='
            KEY_LEFT_BRACKET -> if (shift) '{' else '['
            KEY_RIGHT_BRACKET -> if (shift) '}' else ']'
            KEY_BACKSLASH -> if (shift) '|' else '\\'
            KEY_SEMICOLON -> if (shift) ':' else ';'
            KEY_APOSTROPHE -> if (shift) '"' else '\''
            KEY_GRAVE_ACCENT -> if (shift) '~' else '`'
            KEY_COMMA -> if (shift) '<' else ','
            KEY_PERIOD -> if (shift) '>' else '.'
            KEY_SLASH -> if (shift) '?' else '/'
            KEY_TAB -> '\t'
            KEY_KP_DECIMAL -> '.'
            KEY_KP_DIVIDE -> '/'
            KEY_KP_MULTIPLY -> '*'
            KEY_KP_SUBTRACT -> '-'
            KEY_KP_ADD -> '+'
            KEY_KP_EQUAL -> '='
            else -> null
        }
    }

    private companion object {
        const val NO_SESSION = -1

        // GLFW modifiers
        const val MOD_SHIFT = 0x1
        const val MOD_CONTROL = 0x2

        // GLFW key codes
        const val KEY_SPACE = 32
        const val KEY_APOSTROPHE = 39
        const val KEY_COMMA = 44
        const val KEY_MINUS = 45
        const val KEY_PERIOD = 46
        const val KEY_SLASH = 47
        const val KEY_0 = 48
        const val KEY_9 = 57
        const val KEY_SEMICOLON = 59
        const val KEY_EQUAL = 61
        const val KEY_A = 65
        const val KEY_C = 67
        const val KEY_V = 86
        const val KEY_X = 88
        const val KEY_Z = 90
        const val KEY_LEFT_BRACKET = 91
        const val KEY_BACKSLASH = 92
        const val KEY_RIGHT_BRACKET = 93
        const val KEY_GRAVE_ACCENT = 96
        const val KEY_ENTER = 257
        const val KEY_TAB = 258
        const val KEY_BACKSPACE = 259
        const val KEY_DELETE = 261
        const val KEY_RIGHT = 262
        const val KEY_LEFT = 263
        const val KEY_DOWN = 264
        const val KEY_UP = 265
        const val KEY_HOME = 268
        const val KEY_END = 269
        const val KEY_KP_0 = 320
        const val KEY_KP_9 = 329
        const val KEY_KP_DECIMAL = 330
        const val KEY_KP_DIVIDE = 331
        const val KEY_KP_MULTIPLY = 332
        const val KEY_KP_SUBTRACT = 333
        const val KEY_KP_ADD = 334
        const val KEY_KP_ENTER = 335
        const val KEY_KP_EQUAL = 336

        val SHIFTED_DIGITS = charArrayOf(')', '!', '@', '#', '$', '%', '^', '&', '*', '(')
    }
}
