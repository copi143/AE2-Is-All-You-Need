package allyouneed.client.compose.demo

import allyouneed.client.compose.material.Button
import allyouneed.client.compose.material.Slider
import allyouneed.client.compose.material.Spacer
import allyouneed.client.compose.material.Text
import allyouneed.client.compose.platform.ComposeScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = "Compose Demo",
                color = 0xFFFFAA00.toInt()
            )

            Spacer(Modifier.fillMaxWidth().padding(vertical = 4.dp))

            // Text with dynamic content
            Text(text = text, color = 0xFFFFFFFF.toInt())

            Spacer(Modifier.fillMaxWidth().padding(vertical = 4.dp))

            // Counter
            Row {
                Text("Count: $count", color = 0xFF00FF00.toInt())
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = { count++ },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("+1", color = 0xFFFFFFFF.toInt())
                }
                Button(
                    onClick = { count = 0 },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("Reset", color = 0xFFFF0000.toInt())
                }
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Slider
            Text("Slider: ${(sliderValue * 100).toInt()}%", color = 0xFFAAAAAA.toInt())
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                range = 0f..1f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )

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
