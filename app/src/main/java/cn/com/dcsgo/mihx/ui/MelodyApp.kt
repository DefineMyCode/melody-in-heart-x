@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.com.dcsgo.mihx.core.ui.theme.MelodyTheme
import cn.com.dcsgo.mihx.navigation.MelodyNavHost

@Composable
fun MelodyApp() {
    MelodyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            MelodyNavHost()
        }
    }
}
