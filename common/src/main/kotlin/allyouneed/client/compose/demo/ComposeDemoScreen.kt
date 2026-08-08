package allyouneed.client.compose.demo

import allyouneed.client.compose.material.Text
import allyouneed.client.compose.platform.ComposeScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import net.minecraft.network.chat.Component

class ComposeDemoScreen : ComposeScreen(Component.literal("Compose Demo")) {

    @Composable
    override fun Content() {
        var count by remember { mutableStateOf(0) }
        var sliderValue by remember { mutableStateOf(0.5f) }
        var visible by remember { mutableStateOf(true) }
        var highlight by remember { mutableStateOf(false) }
        val alpha by animateFloatAsState(if (highlight) 1f else 0.3f, label = "demoAlpha")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Compose Demo", color = 0xFFFFAA00.toInt())
            Text("Ctrl+滚轮缩放 UI,当前 %.1fx".format(currentUiScale()), color = 0xFF88FFFF.toInt())

            Spacer(Modifier.fillMaxWidth().padding(vertical = 4.dp))

            // Official material Button
            Row {
                Text("Count: $count", color = 0xFF00FF00.toInt())
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { count++ },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Text("+1", color = 0xFFFFFFFF.toInt())
                }
                Button(
                    onClick = { count = 0 },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Text("Reset", color = 0xFFFF5555.toInt())
                }
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Official material Slider
            Text("Slider: ${(sliderValue * 100).toInt()}%", color = 0xFFAAAAAA.toInt())
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Official animation: animated alpha via graphicsLayer
            Text("Animated alpha (graphicsLayer):", color = 0xFFCCCCCC.toInt())
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(Color(0xFF00AAFF)),
                )
                Spacer(Modifier.size(8.dp))
                Button(onClick = { highlight = !highlight }) {
                    Text(if (highlight) "Dim" else "Bright", color = 0xFFFFFFFF.toInt())
                }
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Official AnimatedVisibility (fade + scale)
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Button(onClick = { visible = !visible }) {
                    Text(if (visible) "Hide" else "Show", color = 0xFFFFFFFF.toInt())
                }
                Spacer(Modifier.size(8.dp))
                AnimatedVisibility(visible = visible) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color(0xFFFF8800)),
                    )
                }
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Color boxes demo
            Text("Color Boxes:", color = 0xFFCCCCCC.toInt())
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFFFF0000))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFF00FF00))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFF0000FF))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFFFFFF00))
                )
            }
        }
    }
}
