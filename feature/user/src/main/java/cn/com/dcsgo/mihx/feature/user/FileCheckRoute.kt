package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.domain.model.DuplicateSongGroup
import cn.com.dcsgo.mihx.domain.model.FileCheckMode
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

@Stable
data class FileCheckRouteState(
    /** 校验结果（未确认前保留） */
    val validationResult: LocalFileValidationResult? = null,
    /** 校验是否正在后台运行 */
    val isValidating: Boolean = false,
    /** 重复歌曲分组（真实路径相同） */
    val duplicateGroups: List<DuplicateSongGroup> = emptyList(),
    /** 是否正在扫描重复 */
    val isScanningDuplicates: Boolean = false,
    /** 是否已执行过重复扫描（区分"未扫"与"扫描后无重复"） */
    val hasScannedDuplicates: Boolean = false,
)

data class FileCheckRouteActions(
    val onBack: () -> Unit,
    /** 启动后台校验（快速/深度由 UI 按钮决定） */
    val onRunValidation: (FileCheckMode) -> Unit,
    /** 确认结果完成（清除结果并返回） */
    val onAcknowledge: () -> Unit,
    /** 启动重复扫描 */
    val onScanDuplicates: () -> Unit = {},
    /** 一键清理全部重复 */
    val onDeduplicateAll: () -> Unit = {},
    /** 打开重复文件详情页 */
    val onOpenDetail: () -> Unit = {},
)

@Composable
fun FileCheckRoute(
    state: FileCheckRouteState,
    actions: FileCheckRouteActions,
) {
    FileCheckScreen(
        validationResult = state.validationResult,
        isValidating = state.isValidating,
        duplicateGroups = state.duplicateGroups,
        isScanningDuplicates = state.isScanningDuplicates,
        hasScannedDuplicates = state.hasScannedDuplicates,
        onBack = actions.onBack,
        onRunValidation = actions.onRunValidation,
        onAcknowledge = actions.onAcknowledge,
        onScanDuplicates = actions.onScanDuplicates,
        onDeduplicateAll = actions.onDeduplicateAll,
        onOpenDetail = actions.onOpenDetail,
    )
}
