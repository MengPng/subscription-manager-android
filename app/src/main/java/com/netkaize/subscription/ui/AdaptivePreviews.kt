package com.netkaize.subscription.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "320dp small phone", widthDp = 320, heightDp = 568, showBackground = true)
@Preview(name = "320dp large text", widthDp = 320, heightDp = 568, fontScale = 2f, showBackground = true)
@Preview(name = "412dp OnePlus baseline", widthDp = 412, heightDp = 915, showBackground = true)
@Preview(name = "700dp landscape", widthDp = 700, heightDp = 360, showBackground = true)
@Preview(name = "1024dp tablet", widthDp = 1024, heightDp = 768, showBackground = true)
@Composable
private fun AdaptiveNavigationPreview() {
    var destination by remember { mutableStateOf(MainDestination.HOME) }
    DingYueTheme {
        AdaptiveNavigationFrame(
            destination = destination,
            onNavigate = { destination = it },
        ) { padding, layout ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppCanvas),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = layout.contentMaxWidthDp.dp)
                        .fillMaxWidth()
                        .padding(layout.pagePaddingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = when (layout.widthClass) {
                            AppWindowWidthClass.COMPACT -> "Compact · 底部导航"
                            AppWindowWidthClass.MEDIUM -> "Medium · 侧栏导航"
                            AppWindowWidthClass.EXPANDED -> "Expanded · 侧栏与双栏内容"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("订阅支出概览", style = MaterialTheme.typography.titleLarge)
                            Text("页面内容会在断点变化时重排，并保留安全边距。")
                        }
                    }
                }
            }
        }
    }
}
