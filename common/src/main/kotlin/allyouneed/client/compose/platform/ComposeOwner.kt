@file:Suppress(
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER",
    "DEPRECATION",
    "DEPRECATION_ERROR",
    "OVERRIDE_DEPRECATION",
)

package allyouneed.client.compose.platform

import androidx.collection.MutableIntObjectMap
import androidx.collection.mutableIntObjectMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.runtime.retain.RetainedValuesStore
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillManager
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.draganddrop.DragAndDropManager
import androidx.compose.ui.draganddrop.DragAndDropNode
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusOwnerImpl
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerInputEventData
import androidx.compose.ui.input.pointer.PointerInputEventProcessor
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PositionCalculator
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.modifier.ModifierLocalManager
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeDrawScope
import androidx.compose.ui.node.MeasureAndLayoutDelegate
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.OwnerSnapshotObserver
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.DefaultUiApplier
import androidx.compose.ui.platform.DefaultViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.EmptySemanticsModifier
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.spatial.ExecuteDelayed
import androidx.compose.ui.spatial.RectManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.InteropView
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Production [Owner] hosting the official androidx.compose.ui layout/measure/draw engine inside
 * Minecraft. The composable tree lives in an official [LayoutNode] root measured against the layer's
 * logical size (from [sizeProvider]); rendering bridges the official Canvas commands into
 * Minecraft's [GuiGraphics] via [McCanvas] — no skiko, no offscreen surface.
 *
 * The owner is decoupled from any [net.minecraft.client.gui.screens.Screen]: a [ComposeLayer] drives
 * it for a full-screen screen or for a sub-region embedded inside an arbitrary existing screen. All
 * pointer input is in **layer-local logical** coordinates; the current mouse position exposed to the
 * tree ([mousePosition]) is in **global logical** coordinates (local + [uiOrigin]) so it stays
 * comparable with `positionInWindow()`.
 */
internal class ComposeOwner(private val sizeProvider: () -> IntSize) : Owner {

    override var density: Density = Density(1f)
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr

    private lateinit var measureAndLayoutDelegate: MeasureAndLayoutDelegate

    override val root = LayoutNode().apply {
        this.layoutDirection = this@ComposeOwner.layoutDirection
        measurePolicy = RootMeasurePolicy
    }

    private val applier = DefaultUiApplier(root)

    /** Suspendable frame clock driven by the game's render loop (see [FrameClock.onNewFrame]). */
    private val frameClock = FrameClock()
    private val scope = CoroutineScope(MinecraftDispatcher + SupervisorJob() + frameClock)
    private val recomposer = Recomposer(effectCoroutineContext = scope.coroutineContext)
    private var composition: Composition? = null

    override val layoutNodes: MutableIntObjectMap<LayoutNode> = mutableIntObjectMapOf()

    override val sharedDrawScope: LayoutNodeDrawScope = LayoutNodeDrawScope()

    private val rootSemanticsNode = EmptySemanticsModifier()
    override val semanticsOwner = SemanticsOwner(root, rootSemanticsNode, layoutNodes)

    private val pendingSnapshotCallbacks = mutableListOf<() -> Unit>()
    override val snapshotObserver = OwnerSnapshotObserver { callback ->
        pendingSnapshotCallbacks += callback
    }

    override val modifierLocalManager = ModifierLocalManager(this)

    private val executeDelayed = object : ExecuteDelayed {
        override fun executeDelayed(delayMillis: Long, callback: () -> Unit): Any {
            callback()
            return Unit
        }

        override fun removeDelayedExecution(token: Any) {}
    }
    override val rectManager = RectManager(layoutNodes, executeDelayed)

    private val platformFocusOwner = object : PlatformFocusOwner {
        override fun requestOwnerFocus(focusDirection: FocusDirection?, previouslyFocusedRect: Rect?): Boolean = false
        override fun clearOwnerFocus() {}
        override fun moveFocusInChildren(focusDirection: FocusDirection): Boolean = false
        override fun getEmbeddedViewFocusRect(): Rect? = null
    }
    override val focusOwner = FocusOwnerImpl(platformFocusOwner, this)

    override val rootForTest: RootForTest = object : RootForTest {
        override val density: Density get() = this@ComposeOwner.density
        override val semanticsOwner: SemanticsOwner get() = this@ComposeOwner.semanticsOwner
        override val textInputService: TextInputService get() = this@ComposeOwner.textInputService
        override fun sendKeyEvent(event: KeyEvent): Boolean = false
    }

    override val hapticFeedBack: HapticFeedback = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
    }

    override val inputModeManager: InputModeManager = object : InputModeManager {
        override val inputMode: InputMode get() = InputMode.Touch
        override fun requestInputMode(inputMode: InputMode): Boolean = false
    }

    override val clipboardManager: ClipboardManager = object : ClipboardManager {
        override fun setText(annotatedString: AnnotatedString) {}
        override fun getText(): AnnotatedString? = null
    }

    override val clipboard: Clipboard = object : Clipboard {
        override suspend fun getClipEntry(): androidx.compose.ui.platform.ClipEntry? = null
        override suspend fun setClipEntry(clipEntry: androidx.compose.ui.platform.ClipEntry?) {}
    }

    override val accessibilityManager: AccessibilityManager = object : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            hasInput: Boolean,
            hasOutput: Boolean,
            isTouchExplorationFocused: Boolean,
        ): Long = originalTimeoutMillis
    }

    override val graphicsContext: GraphicsContext = object : GraphicsContext {
        override fun createGraphicsLayer(): GraphicsLayer =
            error("graphicsLayer {} is not supported by the Compose owner")
        override fun releaseGraphicsLayer(layer: GraphicsLayer) {}
    }

    override val textToolbar: TextToolbar = object : TextToolbar {
        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) {}

        override fun hide() {}
        override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    }

    override val autofillTree = AutofillTree()
    override val autofill: Autofill? = null
    override val autofillManager: AutofillManager? = null

    private val stubPlatformTextInputService = object : PlatformTextInputService {
        override fun startInput(
            value: TextFieldValue,
            imeOptions: ImeOptions,
            onEditCommand: (List<EditCommand>) -> Unit,
            onImeActionPerformed: (ImeAction) -> Unit,
        ) {}

        override fun stopInput() {}
        override fun showSoftwareKeyboard() {}
        override fun hideSoftwareKeyboard() {}
        override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {}
    }
    override val textInputService: TextInputService by lazy { TextInputService(stubPlatformTextInputService) }

    override val softwareKeyboardController: SoftwareKeyboardController = object : SoftwareKeyboardController {
        override fun show() {}
        override fun hide() {}
    }

    override val pointerIconService: PointerIconService = object : PointerIconService {
        private var desiredIcon: PointerIcon? = null
        override fun getIcon(): PointerIcon = desiredIcon ?: PointerIcon.Default
        override fun setIcon(value: PointerIcon?) {
            desiredIcon = value
        }

        private var desiredStylusHoverIcon: PointerIcon? = null
        override fun getStylusHoverIcon(): PointerIcon? = desiredStylusHoverIcon
        override fun setStylusHoverIcon(value: PointerIcon?) {
            desiredStylusHoverIcon = value
        }
    }

    override val windowInfo: WindowInfo = object : WindowInfo {
        override val isWindowFocused: Boolean get() = true
    }

    override val retainedValuesStore: RetainedValuesStore get() = ForgetfulRetainedValuesStore

    override val fontLoader: Font.ResourceLoader = object : Font.ResourceLoader {
        override fun load(font: Font): Any = McTypeface
    }

    override val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }

    override val localeList: LocaleList get() = LocaleList.current

    override var showLayoutBounds: Boolean = false

    override val measureIteration: Long get() = measureAndLayoutDelegate.measureIteration

    override val viewConfiguration: ViewConfiguration get() = createViewConfiguration(density)

    override val coroutineContext: CoroutineContext = EmptyCoroutineContext

    private val pointerInputEventProcessor = PointerInputEventProcessor(root)

    val tooltipHost = TooltipHost()

    /** Per-frame callbacks (smooth-scroll stepping etc.); advanced at the start of [render]. */
    val frameCallbacks = FrameCallbackHost()

    /** Logical-space origin of this layer inside the window, added to `positionInWindow()`. */
    var uiOrigin: Offset = Offset.Zero

    /** Whole-UI zoom factor applied around every render pass (see [setUiScaleFactor]). */
    var uiScale by mutableFloatStateOf(1f)

    fun setUiScaleFactor(scale: Float) {
        uiScale = scale.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
    }

    fun onScreenResize() {
        // A window resize may change the GUI-scaled size without changing root constraints in a
        // way that triggers a measure; force the whole tree to re-measure against the new bounds.
        if (::measureAndLayoutDelegate.isInitialized) measureAndLayoutDelegate.requestRemeasure(root, forced = true)
    }

    private var mouseDown = false
    private var activeButton: PointerButton? = null
    private var hoverPosition: Offset? = null

    /** Logical root-space mouse position for the current frame, updated every render / input event. */
    val mousePosition = MousePosition(IntOffset.Zero)

    private fun updateMousePosition(local: Offset) {
        mousePosition.position = IntOffset(
            (local.x + uiOrigin.x).roundToInt(),
            (local.y + uiOrigin.y).roundToInt(),
        )
    }

    // -------------------------------------------------------------------------------------------
    // Content
    // -------------------------------------------------------------------------------------------

    fun setContent(content: @Composable () -> Unit) {
        // init() is also invoked on window resize; never run a second Recomposer runner.
        if (composition != null) return
        composition = Composition(applier, recomposer).apply {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides density,
                    LocalLayoutDirection provides layoutDirection,
                    LocalViewConfiguration provides createViewConfiguration(density),
                    LocalInputModeManager provides inputModeManager,
                    LocalTooltipHost provides tooltipHost,
                    LocalUiScale provides uiScale,
                    LocalMousePosition provides mousePosition,
                    LocalFrameCallbacks provides frameCallbacks,
                ) {
                    content()
                }
            }
        }
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    // -------------------------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------------------------

    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Drive suspendable animations (Recomposer loop + animation effects) with this frame.
        frameClock.onNewFrame()
        val scale = uiScale
        // Per-frame callbacks (e.g. scroll-state smoothing) run before the snapshot apply / measure
        // / draw of the same frame, so animation refresh rate == game frame rate, no coroutine lag.
        frameCallbacks.advance()
        SnapshotSync.requestApply()
        val size = sizeProvider()
        measureAndLayoutDelegate.updateRootConstraints(
            Constraints(maxWidth = size.width, maxHeight = size.height),
        )
        measureAndLayout()
        dispatchMouseMove(mouseX / scale - uiOrigin.x, mouseY / scale - uiOrigin.y)
        McGraphics.current = graphics
        try {
            graphics.pose().pushPose()
            graphics.pose().translate(uiOrigin.x * scale, uiOrigin.y * scale, 0f)
            graphics.pose().scale(scale, scale, 1f)
            root.draw(McCanvas(graphics), null)
            graphics.pose().popPose()
        } finally {
            McGraphics.current = null
        }
    }

    // -------------------------------------------------------------------------------------------
    // Mouse input (layer-local logical coordinates)
    // -------------------------------------------------------------------------------------------

    fun onMouseClicked(x: Float, y: Float, button: Int): Boolean {
        if (button != 0 && button != 1) return false
        val pointerButton = if (button == 0) PointerButton.Primary else PointerButton.Secondary
        activeButton = pointerButton
        mouseDown = true
        val position = Offset(x, y)
        updateMousePosition(position)
        return processPointerEvent(
            buildPointerEvent(
                eventType = PointerEventType.Press,
                position = position,
                down = true,
                button = pointerButton,
                primary = pointerButton == PointerButton.Primary,
            ),
        )
    }

    fun onMouseReleased(x: Float, y: Float, button: Int): Boolean {
        val pointerButton = activeButton ?: return false
        mouseDown = false
        activeButton = null
        val position = Offset(x, y)
        updateMousePosition(position)
        return processPointerEvent(
            buildPointerEvent(
                eventType = PointerEventType.Release,
                position = position,
                down = false,
                button = pointerButton,
                primary = pointerButton == PointerButton.Primary,
            ),
        )
    }

    fun onMouseScrolled(x: Float, y: Float, delta: Double): Boolean {
        val position = Offset(x, y)
        updateMousePosition(position)
        return processPointerEvent(
            buildPointerEvent(
                eventType = PointerEventType.Scroll,
                position = position,
                down = mouseDown,
                scrollDelta = Offset(0f, delta.toFloat()),
            ),
        )
    }

    private fun dispatchMouseMove(x: Float, y: Float) {
        val position = Offset(x, y)
        updateMousePosition(position)
        val size = sizeProvider()
        val inside = x in 0f..size.width.toFloat() && y in 0f..size.height.toFloat()
        if (!inside) {
            if (hoverPosition != null) {
                hoverPosition = null
                processPointerEvent(
                    buildPointerEvent(PointerEventType.Exit, position, down = mouseDown),
                )
            }
            return
        }
        val previous = hoverPosition
        hoverPosition = position
        val eventType = if (previous == null) PointerEventType.Enter else PointerEventType.Move
        processPointerEvent(buildPointerEvent(eventType, position, down = mouseDown))
    }

    private fun processPointerEvent(event: PointerInputEvent): Boolean {
        val result = pointerInputEventProcessor.process(event, IdentityPositionCalculator)
        return result.value != 0
    }

    // -------------------------------------------------------------------------------------------
    // Owner
    // -------------------------------------------------------------------------------------------

    override fun onRequestMeasure(
        layoutNode: LayoutNode,
        affectsLookahead: Boolean,
        forceRequest: Boolean,
        scheduleMeasureAndLayout: Boolean,
    ) {
        if (affectsLookahead) {
            measureAndLayoutDelegate.requestLookaheadRemeasure(layoutNode, forced = forceRequest)
        } else {
            // LayoutNode.attach fires requestRemeasure before measureAndLayoutDelegate is assigned.
            if (::measureAndLayoutDelegate.isInitialized) measureAndLayoutDelegate.requestRemeasure(layoutNode, forceRequest)
        }
    }

    override fun onRequestRelayout(
        layoutNode: LayoutNode,
        affectsLookahead: Boolean,
        forceRequest: Boolean,
    ) {
        if (affectsLookahead) {
            measureAndLayoutDelegate.requestLookaheadRelayout(layoutNode, forceRequest)
        } else {
            measureAndLayoutDelegate.requestRelayout(layoutNode, forceRequest)
        }
    }

    override fun requestOnPositionedCallback(layoutNode: LayoutNode) {
        measureAndLayoutDelegate.requestOnPositionedCallback(layoutNode)
    }

    override fun onPreAttach(node: LayoutNode) {
        layoutNodes[node.semanticsId] = node
    }

    override fun onPostAttach(node: LayoutNode) {}

    override fun onDetach(node: LayoutNode) {
        layoutNodes.remove(node.semanticsId)
        measureAndLayoutDelegate.onNodeDetached(node)
        snapshotObserver.clear(node)
    }

    override fun calculatePositionInWindow(localPosition: Offset): Offset = localPosition + uiOrigin

    override fun calculateLocalPosition(positionInWindow: Offset): Offset = positionInWindow - uiOrigin

    override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen

    override fun localToScreen(localPosition: Offset): Offset = localPosition

    override fun requestAutofill(node: LayoutNode) {}

    override fun measureAndLayout(sendPointerUpdate: Boolean) {
        if (measureAndLayoutDelegate.hasPendingMeasureOrLayout ||
            measureAndLayoutDelegate.hasPendingOnPositionedCallbacks
        ) {
            measureAndLayoutDelegate.measureAndLayout(null)
            measureAndLayoutDelegate.dispatchOnPositionedCallbacks()
            rectManager.dispatchCallbacks()
        }
    }

    override fun measureAndLayout(layoutNode: LayoutNode, constraints: Constraints) {
        measureAndLayoutDelegate.measureAndLayout(layoutNode, constraints)
        if (!measureAndLayoutDelegate.hasPendingMeasureOrLayout) {
            measureAndLayoutDelegate.dispatchOnPositionedCallbacks()
        }
        rectManager.dispatchCallbacks()
    }

    override fun forceMeasureTheSubtree(layoutNode: LayoutNode, affectsLookahead: Boolean) {
        measureAndLayoutDelegate.forceMeasureTheSubtree(layoutNode, affectsLookahead)
    }

    override fun createLayer(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
        explicitLayer: GraphicsLayer?,
    ): OwnedLayer = PassthroughLayer(drawBlock, invalidateParentLayer)

    override fun onSemanticsChange() {}
    override fun onLayoutChange(layoutNode: LayoutNode) {}
    override fun onLayoutNodeDeactivated(layoutNode: LayoutNode) {}

    @OptIn(InternalComposeUiApi::class)
    override fun onInteropViewLayoutChange(view: InteropView) {}

    private val endApplyChangesListeners = mutableListOf<() -> Unit>()

    override fun onEndApplyChanges() {
        if (pendingSnapshotCallbacks.isNotEmpty()) {
            val callbacks = pendingSnapshotCallbacks.toList()
            pendingSnapshotCallbacks.clear()
            callbacks.forEach { it() }
        }
        val listeners = endApplyChangesListeners.toList()
        endApplyChangesListeners.clear()
        listeners.forEach { it() }
    }

    override fun registerOnEndApplyChangesListener(listener: () -> Unit) {
        endApplyChangesListeners += listener
    }

    override fun registerOnLayoutCompletedListener(listener: Owner.OnLayoutCompletedListener) {
        measureAndLayoutDelegate.registerOnLayoutCompletedListener(listener)
    }

    override val dragAndDropManager: DragAndDropManager = object : DragAndDropManager {
        override val modifier: Modifier get() = Modifier
        override val isRequestDragAndDropTransferRequired: Boolean get() = false
        override fun requestDragAndDropTransfer(dragAndDropNode: DragAndDropNode, offset: Offset) {}
        override fun registerTargetInterest(dragAndDropTarget: DragAndDropTarget) {}
        override fun isInterestedTarget(dragAndDropTarget: DragAndDropTarget): Boolean = false
    }

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope.() -> Nothing,
    ): Nothing = error("text input is not supported by the Compose owner")

    fun dispose() {
        composition?.dispose()
        recomposer.cancel()
        scope.cancel()
    }

    // Attach after every property has been initialized: LayoutNode.attach reads owner.rectManager
    // (declared further up), and MeasureAndLayoutDelegate needs root.owner already attached.
    init {
        root.attach(this)
        measureAndLayoutDelegate = MeasureAndLayoutDelegate(root)
    }
}

private fun buildPointerEvent(
    eventType: PointerEventType,
    position: Offset,
    down: Boolean,
    button: PointerButton? = null,
    scrollDelta: Offset = Offset.Zero,
    primary: Boolean = true,
): PointerInputEvent {
    val uptime = System.nanoTime() / 1_000_000L
    return PointerInputEvent(
        eventType = eventType,
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
                scrollDelta = scrollDelta,
                scaleGestureFactor = 1f,
                panGestureOffset = Offset.Zero,
                originalEventPosition = position,
            ),
        ),
        buttons = when {
            !down -> PointerButtons()
            primary -> PointerButtons(isPrimaryPressed = true)
            else -> PointerButtons(isSecondaryPressed = true)
        },
        keyboardModifiers = PointerKeyboardModifiers(),
        button = button,
    )
}

private object IdentityPositionCalculator : PositionCalculator {
    override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen
    override fun localToScreen(localPosition: Offset): Offset = localPosition
}

private object McTypeface : Typeface {
    override val fontFamily: FontFamily get() = FontFamily.Default
}

@Suppress("DEPRECATION")
private fun createViewConfiguration(density: Density): ViewConfiguration = DefaultViewConfiguration(density)

internal object SnapshotSync {
    fun requestApply() {
        Snapshot.sendApplyNotifications()
    }
}

/**
 * Dispatches coroutines onto the Minecraft client (game) thread — the thread that owns
 * the UI. It replaces [kotlinx.coroutines.Dispatchers.Main], which requires a platform
 * provider (swing/android/javafx) that is not available inside Minecraft.
 */
private object MinecraftDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !Minecraft.getInstance().isSameThread

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Minecraft.getInstance().execute(block)
    }
}

/**
 * A [MonotonicFrameClock] aligned to the game's render loop. [withFrameNanos] suspends until the
 * next [ComposeOwner.render] pass, which resumes all waiters via [onNewFrame] on the game thread.
 * This is what makes compose animations (animate*AsState, Animatable, AnimatedVisibility...) advance
 * one step per rendered frame instead of completing instantly.
 */
private class FrameClock : MonotonicFrameClock {
    private val awaiters = mutableListOf<CancellableContinuation<Long>>()

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        val frameTime = suspendCancellableCoroutine<Long> { continuation ->
            awaiters += continuation
            continuation.invokeOnCancellation { awaiters.remove(continuation) }
        }
        return onFrame(frameTime)
    }

    fun onNewFrame() {
        if (awaiters.isEmpty()) return
        val frameTime = System.nanoTime()
        val pending = awaiters.toList()
        awaiters.clear()
        for (continuation in pending) continuation.resume(frameTime)
    }
}

private const val MIN_UI_SCALE = 0.5f
private const val MAX_UI_SCALE = 4f
