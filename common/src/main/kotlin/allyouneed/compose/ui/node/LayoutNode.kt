package allyouneed.compose.ui.node

import allyouneed.compose.ui.draw.McDrawScope
import allyouneed.compose.ui.layout.*
import allyouneed.compose.ui.modifier.*

class LayoutNode {
    var parent: LayoutNode? = null
    val children = mutableListOf<LayoutNode>()

    var modifier: Modifier = Modifier
    var measurePolicy: MeasurePolicy? = null
    var measureScope: MeasureScope = MeasureScope()

    var x: Int = 0
    var y: Int = 0
    var width: Int = 0
    var height: Int = 0

    var scrollOffsetX: Int = 0
    var scrollOffsetY: Int = 0

    internal var placeables: List<Placeable> = emptyList()

    val measurable = object : Measurable {
        override fun measure(constraints: Constraints): Placeable = this@LayoutNode.remeasure(constraints)
    }

    fun remeasure(constraints: Constraints): Placeable {
        val policy = measurePolicy
        if (policy != null) {
            val childMeasurables = children.map { it.measurable }
            val result = policy.measure(measureScope, childMeasurables, constraints)
            width = result.width
            height = result.height
            placeables = result.placeables
            result.placeChildren()
        } else if (children.size == 1) {
            val p = children[0].remeasure(constraints)
            width = p.width
            height = p.height
            placeables = listOf(p)
            p.place(0, 0)
        } else {
            width = constraints.constrainWidth(0)
            height = constraints.constrainHeight(0)
            placeables = emptyList()
        }

        // 应用 ScrollModifier 的 scrollOffset
        modifier.foldElements {
            if (it is ScrollModifier) {
                scrollOffsetY = -it.scrollState.value
            }
        }

        return Placeable(width, height) { px, py ->
            this.x = px
            this.y = py
        }
    }

    fun draw(scope: McDrawScope) {
        scope.pushPose()
        scope.translate(x, y)

        val w = width
        val h = height
        val oldW = scope.currentWidth
        val oldH = scope.currentHeight
        scope.currentWidth = w
        scope.currentHeight = h

        val drawMods = collectDrawModifiers()
        if (drawMods.isNotEmpty()) {
            var currentDraw: () -> Unit = { drawChildren(scope) }
            for (mod in drawMods.reversed()) {
                val next = currentDraw
                currentDraw = { mod.draw(scope, next) }
            }
            currentDraw()
        } else {
            drawChildren(scope)
        }

        scope.currentWidth = oldW
        scope.currentHeight = oldH
        scope.popPose()
    }

    private fun drawChildren(scope: McDrawScope) {
        for (child in children) {
            scope.pushPose()
            scope.translate(scrollOffsetX, scrollOffsetY)
            child.draw(scope)
            scope.popPose()
        }
    }

    private fun collectDrawModifiers(): List<DrawModifier> {
        val result = mutableListOf<DrawModifier>()
        modifier.foldElements { if (it is DrawModifier) result.add(it) }
        return result
    }
}
