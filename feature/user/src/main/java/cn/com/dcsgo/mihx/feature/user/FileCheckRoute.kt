package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

@Stable
data class FileCheckRouteState(
    /** 校验结果（未确认前保留） */
    val validationResult: LocalFileValidationResult? = null,
    /** 校验是否正在后台运行 */
    val isValidating: Boolean = false,
)

data class FileCheckRouteActions(
    val onBack: () -> Unit,
    /** 启动后台校验 */
    val onRunValidation: () -> Unit,
    /** 确认结果完成（清除结果并返回） */
    val onAcknowledge: () -> Unit,
)

@Composable
fun FileCheckRoute(
    state: FileCheckRouteState,
    actions: FileCheckRouteActions,
) {
    FileCheckScreen(
        validationResult = state.validationResult,
        isValidating = state.isValidating,
        onBack = actions.onBack,
        onRunValidation = actions.onRunValidation,
        onAcknowledge = actions.onAcknowledge,
    )
}
