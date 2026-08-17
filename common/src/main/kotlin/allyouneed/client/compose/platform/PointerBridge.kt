package allyouneed.client.compose.platform

import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers

internal fun pointerButtonOf(button: Int): PointerButton? = when (button) {
    0 -> PointerButton.Primary
    1 -> PointerButton.Secondary
    2 -> PointerButton.Tertiary
    else -> null
}

internal fun pointerButtonsOf(
    primary: Boolean = false,
    secondary: Boolean = false,
    tertiary: Boolean = false,
): PointerButtons = PointerButtons(
    isPrimaryPressed = primary,
    isSecondaryPressed = secondary,
    isTertiaryPressed = tertiary,
)

internal fun keyboardModifiersOf(
    shift: Boolean = false,
    ctrl: Boolean = false,
    alt: Boolean = false,
    meta: Boolean = false,
): PointerKeyboardModifiers = PointerKeyboardModifiers(
    isShiftPressed = shift,
    isCtrlPressed = ctrl,
    isAltPressed = alt,
    isMetaPressed = meta,
)
