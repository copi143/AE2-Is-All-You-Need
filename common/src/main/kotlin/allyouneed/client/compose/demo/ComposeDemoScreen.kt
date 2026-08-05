package allyouneed.client.compose.demo

import allyouneed.client.compose.material.Button
import allyouneed.client.compose.material.Slider
import allyouneed.client.compose.material.Spacer
import allyouneed.client.compose.material.Text
import allyouneed.client.compose.platform.ComposeScreen
import allyouneed.client.compose.ui.layout.Column
import allyouneed.client.compose.ui.layout.Row
import allyouneed.client.compose.ui.modifier.*
import androidx.compose.runtime.*
import net.minecraft.network.chat.Component

class ComposeDemoScreen : ComposeScreen(Component.literal("Compose Demo")) {

    @Composable
    override fun Content() {
        var count by remember { mutableStateOf(0) }
        var sliderValue by remember { mutableStateOf(0.5f) }
        var text by remember { mutableStateOf("Hello Compose!") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16)
        ) {
            // Title
            Text(
                text = "Compose Demo",
                color = 0xFFFFAA00.toInt()
            )

            Spacer(Modifier.fillMaxWidth().padding(vertical = 4))

            // Text with dynamic content
            Text(text = text, color = 0xFFFFFFFF.toInt())

            Spacer(Modifier.fillMaxWidth().padding(vertical = 4))

            // Counter
            Row {
                Text("Count: $count", color = 0xFF00FF00.toInt())
                Spacer(Modifier.fillMaxWidth().padding(horizontal = 8))
                Button(
                    onClick = { count++ },
                    modifier = Modifier.padding(horizontal = 4)
                ) {
                    Text("+1", color = 0xFFFFFFFF.toInt())
                }
                Button(
                    onClick = { count = 0 },
                    modifier = Modifier.padding(horizontal = 4)
                ) {
                    Text("Reset", color = 0xFFFF0000.toInt())
                }
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8))

            // Slider
            Text("Slider: ${(sliderValue * 100).toInt()}%", color = 0xFFAAAAAA.toInt())
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                range = 0f..1f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4)
            )

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8))

            // Color boxes demo
            Text("Color Boxes:", color = 0xFFCCCCCC.toInt())
            Row(modifier = Modifier.padding(vertical = 4)) {
                Box(color = 0xFFFF0000.toInt(), size = 40)
                Box(color = 0xFF00FF00.toInt(), size = 40)
                Box(color = 0xFF0000FF.toInt(), size = 40)
                Box(color = 0xFFFFFF00.toInt(), size = 40)
            }
        }
    }

    @Composable
    private fun Box(color: Int, size: Int) {
        allyouneed.client.compose.ui.layout.Box(
            modifier = Modifier
                .size(size)
                .padding(horizontal = 2)
                .background(color)
        ) {}
    }
}
