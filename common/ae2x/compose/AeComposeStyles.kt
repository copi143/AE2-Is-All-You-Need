package ae2x.compose

import appeng.client.gui.style.ScreenStyle
import appeng.client.gui.style.StyleManager

object AeComposeStyles {
    const val BLANK_PATH = "/screens/compose_blank.json"

    fun blank(): ScreenStyle = StyleManager.loadStyleDoc(BLANK_PATH)
}
