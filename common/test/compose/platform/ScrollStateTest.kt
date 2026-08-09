package allyouneed.compose.platform

import allyouneed.client.compose.platform.ScrollState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ScrollStateTest {

    private var now = 0L

    /** One game frame at 60 FPS in nanoseconds. */
    private val FRAME_NANOS = 16_000_000L

    private fun state(maxScroll: Float = 100f, smoothingTime: Float = 0.06f): ScrollState =
        ScrollState(maxScroll = maxScroll, smoothingTime = smoothingTime) { now }

    @Test
    fun `wheel scroll moves target only and clamps`() {
        val s = state()
        s.scrollBy(50f)
        assertEquals(50f, s.target, 1e-6f)
        assertEquals(0f, s.display, 1e-6f)

        s.scrollBy(200f)
        assertEquals(100f, s.target, 1e-6f)

        s.scrollBy(-500f)
        assertEquals(0f, s.target, 1e-6f)
    }

    @Test
    fun `scrollBy is a no-op when there is nothing to scroll`() {
        val s = state(maxScroll = 0f)
        s.scrollBy(50f)
        assertEquals(0f, s.target, 1e-6f)
        assertEquals(0f, s.display, 1e-6f)
        assertFalse(s.isAnimating)
    }

    @Test
    fun `advance converges display to target over frames`() {
        val s = state()
        s.scrollBy(100f)
        assertTrue(s.isAnimating)

        var frames = 0
        while (abs(s.target - s.display) > 0.01f) {
            now += FRAME_NANOS
            s.advance()
            frames++
            assertTrue(frames < 1000, "display failed to converge in $frames frames")
        }
        assertEquals(100f, s.display, 0.01f)
        assertFalse(s.isAnimating)
    }

    @Test
    fun `re-targeting mid-animation does not reset display`() {
        val s = state()
        s.scrollBy(100f)
        s.advance() // prime the clock
        now += FRAME_NANOS
        repeat(2) {
            s.advance()
            now += FRAME_NANOS
        }
        val midway = s.display
        assertTrue(midway in 0f..100f)
        assertTrue(midway > 0f)

        s.scrollBy(-40f)
        assertEquals(60f, s.target, 1e-6f)
        assertTrue(s.display >= midway)
    }

    @Test
    fun `seek scrubs immediately`() {
        val s = state()
        s.seek(40f)
        assertEquals(40f, s.display, 1e-6f)
        assertEquals(40f, s.target, 1e-6f)
        assertFalse(s.isAnimating)

        s.seek(-10f)
        assertEquals(0f, s.display, 1e-6f)

        s.seek(999f)
        assertEquals(100f, s.display, 1e-6f)
    }

    @Test
    fun `advance at rest writes nothing`() {
        val s = state()
        s.seek(0f)
        now += FRAME_NANOS
        s.advance()
        assertEquals(0f, s.display, 1e-6f)
        assertFalse(s.isAnimating)

        now += FRAME_NANOS
        s.advance()
        assertEquals(0f, s.display, 1e-6f)
    }

    @Test
    fun `first advance primes the clock without moving`() {
        val s = state()
        s.scrollBy(10f)
        s.advance()
        assertEquals(0f, s.display, 1e-6f)

        now += FRAME_NANOS
        s.advance()
        assertTrue(s.display > 0f)
    }
}
