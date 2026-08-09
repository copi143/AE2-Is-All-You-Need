@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package allyouneed.compose.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.DefaultUiApplier
import androidx.compose.ui.platform.LocalDensity
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
 * Spike acceptance: the official androidx.compose Column/Box lay out correctly under our
 * self-hosted [SpikeOwner], and official drawing primitives (Modifier.background) reach a
 * self-made [Canvas] implementation, with no skiko native library loaded.
 */
class OfficialOwnerSpikeTest {

    private fun host(content: @Composable () -> Unit): SpikeOwner {
        val owner = SpikeOwner(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
        )
        val applier = DefaultUiApplier(owner.root)
        val recomposer = Recomposer(effectCoroutineContext = EmptyCoroutineContext)
        val composition = Composition(applier, recomposer)
        composition.setContent {
            CompositionLocalProvider(
                LocalDensity provides owner.density,
                LocalLayoutDirection provides owner.layoutDirection,
                LocalViewConfiguration provides owner.viewConfiguration,
            ) {
                content()
            }
        }
        owner.setRootConstraints(Constraints(maxWidth = 320, maxHeight = 240))
        owner.measureRoot()
        return owner
    }

    private fun SpikeOwner.measureRoot() {
        setRootConstraints(Constraints(maxWidth = 320, maxHeight = 240))
        measureAndLayout(sendPointerUpdate = false)
    }

    @Test
    fun `official Column lays children out vertically under SpikeOwner`() {
        val owner = host {
            Column {
                Box(Modifier.size(10.dp))
                Box(Modifier.size(20.dp, 30.dp))
            }
        }
        val col = owner.root.children[0]
        val b0 = col.children[0]
        val b1 = col.children[1]

        assertEquals(20, col.width)
        assertEquals(40, col.height)
        assertEquals(10, b0.width)
        assertEquals(10, b0.height)
        assertEquals(20, b1.width)
        assertEquals(30, b1.height)

        assertEquals(Offset.Zero, b0.coordinates.localToRoot(Offset.Zero))
        assertEquals(Offset(0f, 10f), b1.coordinates.localToRoot(Offset.Zero))
    }

    @Test
    fun `official background drawing reaches the self-made Canvas without skiko`() {
        val owner = host {
            Box(Modifier.size(20.dp).background(Color.Red))
        }
        owner.measureRoot()

        val canvas = RecordingCanvas()
        owner.root.outerCoordinator.draw(canvas, null)

        assertTrue(canvas.fills.isNotEmpty(), "expected at least one filled rect")
        val fill = canvas.fills.first()
        assertEquals(20f, fill.right - fill.left)
        assertEquals(20f, fill.bottom - fill.top)
        assertEquals(Color.Red.value, fill.color)
    }

    @Test
    fun `offset placement is pushed into the Canvas via translate`() {
        val owner = host {
            Column(Modifier.padding(top = 10.dp)) {
                Box(Modifier.size(20.dp).background(Color.Blue))
            }
        }
        owner.measureRoot()

        val canvas = RecordingCanvas()
        owner.root.outerCoordinator.draw(canvas, null)

        assertTrue(canvas.translations.isNotEmpty())
        assertTrue(canvas.translations.any { it.x == 0f && it.y == 10f },
            "expected a translate to the padded child offset, got ${canvas.translations}")
        assertTrue(canvas.fills.isNotEmpty())
    }
}
