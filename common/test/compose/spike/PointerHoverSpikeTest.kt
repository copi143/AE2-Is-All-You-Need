@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DEPRECATION")

package allyouneed.compose.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerInputEventData
import androidx.compose.ui.input.pointer.PointerInputEventProcessor
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.DefaultUiApplier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the official hover enter/exit state machine under our self-hosted owner: a composable
 * with a pointerInput must keep receiving events (with correctly translated local positions) after
 * the cursor leaves its bounds, so a tooltip / hover highlight can be cleared on exit.
 */
class PointerHoverSpikeTest {

    private data class Received(val type: PointerEventType, val pos: Offset)

    private class HoverHost {
        val owner: SpikeOwner
        val processor: PointerInputEventProcessor
        val events = mutableListOf<Received>()

        constructor(content: @Composable HoverHost.() -> Unit) {
            owner = SpikeOwner(Density(1f), LayoutDirection.Ltr)
            val applier = DefaultUiApplier(owner.root)
            val recomposer = Recomposer(effectCoroutineContext = EmptyCoroutineContext)
            val composition = Composition(applier, recomposer)
            composition.setContent {
                CompositionLocalProvider(
                    LocalDensity provides owner.density,
                    LocalLayoutDirection provides owner.layoutDirection,
                    LocalViewConfiguration provides owner.viewConfiguration,
                    LocalInputModeManager provides owner.inputModeManager,
                ) {
                    with(this@HoverHost) { content() }
                }
            }
            owner.setRootConstraints(Constraints(maxWidth = 320, maxHeight = 240))
            owner.measureAndLayout(sendPointerUpdate = false)
            processor = PointerInputEventProcessor(owner.root)
        }

        fun move(x: Float, y: Float, down: Boolean = false) {
            val uptime = System.nanoTime() / 1_000_000L
            val position = Offset(x, y)
            processor.process(
                PointerInputEvent(
                    eventType = PointerEventType.Move,
                    uptime = uptime,
                    pointers = listOf(
                        PointerInputEventData(
                            id = PointerId(0),
                            uptime = uptime,
                            positionOnScreen = position,
                            position = position,
                            down = down,
                            pressure = if (down) 1f else 0f,
                            type = PointerType.Mouse,
                            activeHover = !down,
                            historical = emptyList(),
                            scrollDelta = Offset.Zero,
                            scaleGestureFactor = 1f,
                            panGestureOffset = Offset.Zero,
                            originalEventPosition = position,
                        ),
                    ),
                    buttons = if (down) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
                    keyboardModifiers = PointerKeyboardModifiers(),
                    button = null,
                ),
                object : androidx.compose.ui.input.pointer.PositionCalculator {
                    override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen
                    override fun localToScreen(localPosition: Offset): Offset = localPosition
                },
            )
        }
    }

    private fun makeHost(content: @Composable HoverHost.() -> Unit): HoverHost {
        return HoverHost(content)
    }

    private fun makeItemDetailsHost(): HoverHost {
        return makeHost {
            // Mirror the item-details screen: centred panel; slot at (8,8) 18x18; a clickable "✕"
            // at the far right of the header; a scrollbar track with a pointerInput at the right
            // edge of the content area; a row of no-pointer-input content boxes beneath the slot.
            Box(Modifier.size(320.dp, 240.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Box(Modifier.size(240.dp, 160.dp)) {
                    Box(
                        Modifier
                            .offset(8.dp, 8.dp)
                            .size(18.dp, 18.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val change = e.changes.firstOrNull() ?: continue
                                        events += Received(e.type, change.position)
                                    }
                                }
                            },
                    )
                    Box(
                        Modifier
                            .offset(212.dp, 8.dp)
                            .size(14.dp, 14.dp)
                            .background(Color.Gray)
                            .clickable {},
                    )
                    Box(
                        Modifier
                            .offset(8.dp, 32.dp)
                            .size(60.dp, 80.dp)
                            .background(Color.Black),
                    )
                    Box(
                        Modifier
                            .offset(64.dp, 32.dp)
                            .size(4.dp, 80.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val change = e.changes.firstOrNull() ?: continue
                                        // scrollbar just consumes its own events; no recording
                                        change.consume()
                                    }
                                }
                            },
                    )
                }
            }
        }
    }

    @Test
    fun `hovered slot receives events after the cursor leaves its bounds`() {
        val host = makeItemDetailsHost()
        // Panel is centred: left = (320-240)/2 = 40, top = (240-160)/2 = 40.
        // Slot occupies (48,48)-(66,66).
        host.move(57f, 57f) // inside the slot
        host.move(70f, 57f) // just outside to the right (x > 66)

        assertTrue(host.events.isNotEmpty(), "expected at least the initial hover event")
        val first = host.events.first()
        assertEquals(PointerEventType.Enter, first.type)
        assertTrue(
            first.pos.x in 0f..18f && first.pos.y in 0f..18f,
            "expected an in-bounds position, got $first",
        )
        val outside = host.events.last()
        assertTrue(
            outside.pos.x !in 0f..18f || outside.pos.y > 18f || outside.pos.y < 0f,
            "expected an out-of-bounds local position, got $outside",
        )
    }

    @Test
    fun `slot keeps getting out-of-bounds moves while the cursor rests outside`() {
        val host = makeItemDetailsHost()
        host.move(57f, 57f) // inside
        // Simulate per-frame stationary dispatch like the real owner: many identical events.
        for (i in 0 until 5) host.move(70f, 57f)

        assertTrue(host.events.size >= 2, "expected enter + follow-up events, got ${host.events}")
        assertTrue(
            host.events.drop(1).all { r -> r.pos.x > 18f },
            "all post-enter events should keep out-of-bounds positions, got ${host.events}",
        )
    }

    @Test
    fun `pressing elsewhere and releasing does not break subsequent hover exit`() {
        val host = makeItemDetailsHost()
        host.move(57f, 57f) // hover the slot
        // Click on the "✕" (panel x=212..226 -> screen x=252..266, y=48..62).
        host.move(259f, 55f, down = true)
        host.move(259f, 55f, down = false)
        // Return the cursor to the slot, then leave it.
        host.move(57f, 57f)
        host.move(70f, 57f)

        val last = host.events.last()
        assertTrue(
            last.pos.x > 18f || last.pos.x < 0f || last.pos.y > 18f || last.pos.y < 0f,
            "expected an out-of-bounds position after re-hover + leave, got $last (all=${host.events})",
        )
    }

    @Test
    fun `leaving in every direction produces out of bounds positions`() {
        val host = makeItemDetailsHost()
        val center = Offset(57f, 57f)
        host.move(center.x, center.y)

        val directions = listOf(
            Offset(70f, 57f),  // right
            Offset(57f, 70f),  // down
            Offset(46f, 57f),  // left
            Offset(57f, 46f),  // up
            Offset(74f, 74f),  // down-right diagonal
        )
        for (d in directions) host.move(d.x, d.y)

        // After the in-bounds enter, every subsequent event must be out of bounds.
        val outOfBounds = host.events.drop(1).map { r ->
            r.pos.x !in 0f..18f || r.pos.y < 0f || r.pos.y > 18f
        }
        assertTrue(
            outOfBounds.all { it },
            "all post-enter events should be out of bounds, got ${host.events}",
        )
    }
}
