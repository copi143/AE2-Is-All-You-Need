@file:Suppress(
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER",
    "DEPRECATION",
    "DEPRECATION_ERROR",
    "OVERRIDE_DEPRECATION",
)

package allyouneed.compose.spike

import androidx.collection.MutableIntObjectMap
import androidx.collection.mutableIntObjectMapOf
import androidx.compose.runtime.State
import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.runtime.retain.RetainedValuesStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.draganddrop.DragAndDropManager
import androidx.compose.ui.draganddrop.DragAndDropNode
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusOwnerImpl
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerIconService
import androidx.compose.ui.input.pointer.PositionCalculator
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.modifier.ModifierLocalManager
import androidx.compose.ui.semantics.EmptySemanticsModifier
import androidx.compose.ui.spatial.ExecuteDelayed
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeDrawScope
import androidx.compose.ui.node.MeasureAndLayoutDelegate
import androidx.compose.ui.node.Owner
import allyouneed.client.compose.platform.PassthroughLayer
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.node.OwnerSnapshotObserver
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.DefaultViewConfiguration
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsOwner
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.InteropView
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.coroutineContext

/**
 * Spike: minimal host implementing the official [Owner] interface without any skiko dependency.
 * Layout/measure/modifier handling is delegated to the official [MeasureAndLayoutDelegate].
 */
internal class SpikeOwner(
    override var density: Density,
    override var layoutDirection: LayoutDirection,
) : Owner {

    private val measureAndLayoutDelegate: MeasureAndLayoutDelegate

    override val root = LayoutNode().apply {
        this.layoutDirection = this@SpikeOwner.layoutDirection
        measurePolicy = RootMeasurePolicy
    }

    init {
        measureAndLayoutDelegate = MeasureAndLayoutDelegate(root)
    }

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
        override val density: Density get() = this@SpikeOwner.density
        override val semanticsOwner: SemanticsOwner get() = this@SpikeOwner.semanticsOwner
        override val textInputService: TextInputService get() = this@SpikeOwner.textInputService
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
            error("graphicsLayer {} is not supported by the Spike owner")
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
    override val autofill: androidx.compose.ui.autofill.Autofill? = null
    override val autofillManager: androidx.compose.ui.autofill.AutofillManager? = null

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

    @Suppress("DEPRECATION")
    override val viewConfiguration: ViewConfiguration get() = createViewConfiguration(density)

    override val coroutineContext = EmptyCoroutineContext

    override fun onRequestMeasure(
        layoutNode: LayoutNode,
        affectsLookahead: Boolean,
        forceRequest: Boolean,
        scheduleMeasureAndLayout: Boolean,
    ) {
        if (affectsLookahead) {
            measureAndLayoutDelegate.requestLookaheadRemeasure(layoutNode, forceRequest)
        } else {
            measureAndLayoutDelegate.requestRemeasure(layoutNode, forceRequest)
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

    override fun calculatePositionInWindow(localPosition: Offset): Offset = localPosition

    override fun calculateLocalPosition(positionInWindow: Offset): Offset = positionInWindow

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
    ): Nothing = error("text input is not supported by the Spike owner")

    init {
        snapshotObserver.startObserving()
        root.attach(this)
    }

    fun setRootConstraints(constraints: Constraints) {
        measureAndLayoutDelegate.updateRootConstraints(constraints)
    }
}

private object McTypeface : Typeface {
    override val fontFamily: FontFamily get() = FontFamily.Default
}

@Suppress("DEPRECATION")
private fun createViewConfiguration(density: Density): ViewConfiguration = DefaultViewConfiguration(density)

