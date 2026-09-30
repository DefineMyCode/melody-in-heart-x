package cn.com.dcsgo.mihx.feature.user

import cn.com.dcsgo.mihx.core.skin.SkinIssue

/**
 * P5 导入结果。
 *
 * sealed 分四档，让 [cn.com.dcsgo.mihx.feature.user.UserSkinRoute] 直接 when 分发:
 *  - [Success] 装入并应用 → 提示"已切换到 X"
 *  - [Failed] 校验不通过 → 显示完整 issue 列表（Q2）
 *  - [NotHandled] release 或未启用场景 → 提示"该功能在正式版不可用"
 *  - [Error] IO 异常等 → 通用错误提示
 */
sealed class ImportSkinResult {
    data class Success(val id: String, val name: String) : ImportSkinResult()
    data class Failed(val issues: List<SkinIssue>) : ImportSkinResult()
    data object NotHandled : ImportSkinResult()
    data class Error(val message: String) : ImportSkinResult()
}
