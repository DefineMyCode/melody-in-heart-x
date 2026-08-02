@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * Theme-synced top app bar.
 *
 * material3 1.3.x removed the 1.2.x `colorTransitionSpec` knob and animates the bar container
 * colour with a spring on every colour change, so on a theme switch the title bar trails the page
 * body by a few hundred milliseconds. Keying the bar on the active colour scheme forces a rebuild
 * on theme change (the internal [animateColorAsState] then starts at the target colour), so the
 * bar and the page body switch in the same frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelodyTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
) {
    key(MaterialTheme.colorScheme.surface) {
        TopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
    }
}
