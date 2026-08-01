package cn.com.dcsgo.mihx.core.ui.toast

import androidx.compose.runtime.compositionLocalOf

/**
 * Process-wide [ToastController] accessor. Provided once near the app root (see
 * `cn.com.dcsgo.mihx.ui.MelodyApp`); any composable in the hierarchy can call
 * `LocalToastController.current.show(...)` to surface a top toast, replacing Snackbar usage.
 *
 * This keeps the toast plumbing in [:core:ui] so feature modules (which must not depend on [:app],
 * gate A2) can still raise toasts without reaching into app-level code.
 */
val LocalToastController = compositionLocalOf<ToastController> {
    error(
        "No ToastController provided. Wrap the composition in " +
            "CompositionLocalProvider(LocalToastController provides ...) somewhere above this call.",
    )
}
