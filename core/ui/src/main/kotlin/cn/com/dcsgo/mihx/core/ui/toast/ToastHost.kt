@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Top-anchored toast host that replaces Snackbar across the app (plan P3-8). */
@Composable
fun ToastHost(controller: ToastController, modifier: Modifier = Modifier) {
    val messages by controller.messages.collectAsState()
    Column(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        messages.forEach { message ->
            LaunchedEffect(message.id) {
                delay(message.durationMs)
                controller.dismiss(message.id)
            }
            AnimatedVisibility(visible = true) {
                Surface(
                    shadowElevation = 4.dp,
                    // P3-8: tap anywhere on the toast to dismiss it manually.
                    modifier = Modifier.clickable { controller.dismiss(message.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = message.text,
                            modifier = Modifier.fillMaxWidth(0.88f),
                        )
                        Text(text = "✕")
                    }
                }
            }
        }
    }
}
