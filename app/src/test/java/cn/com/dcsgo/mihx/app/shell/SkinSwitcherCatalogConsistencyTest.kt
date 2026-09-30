package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.SkinPartCatalog
import cn.com.dcsgo.mihx.feature.user.UserSections
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 样式切换分区跨模块一致性测试（2026-09-30 替代原 UserSkinCatalogConsistencyTest）。
 *
 * ## 为什么放在 :app 而不是 :core:skin 或 :feature:user
 *
 * - :core:skin 反向依赖 :feature:user 违反架构门禁
 * - :feature:user 看不到 :core:skin
 * - :app 两个都能看到,**最适合做跨模块一致性回归**
 *
 * ## 防的 bug 类型
 *
 * "UserSections.SKIN_SWITCHER 字符串改了但 SkinPartCatalog 没同步"(或反之)
 * → 描述合法但装配端不认 / 装配端认但描述校验拒, 与 L3 ROW_TEMPLATES
 * vs SONG_LIST_TEMPLATES 漂移是同一类陷阱。
 *
 * 负向对照: 临时把 SkinPartCatalog.SKIN_SWITCHER 改成 "skin-custom" → 1 条失败 → 还原恢复全绿。
 */
class SkinSwitcherCatalogConsistencyTest {

    @Test
    fun `UserSections_SKIN_SWITCHER matches SkinPartCatalog_SKIN_SWITCHER exactly`() {
        assertEquals(
            "UserSections.SKIN_SWITCHER 必须与 SkinPartCatalog.SKIN_SWITCHER 字面相等 (两边都要改)",
            SkinPartCatalog.SKIN_SWITCHER,
            UserSections.SKIN_SWITCHER,
        )
    }
}
