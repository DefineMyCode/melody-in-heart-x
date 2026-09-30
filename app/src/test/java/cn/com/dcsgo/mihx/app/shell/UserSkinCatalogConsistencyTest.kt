package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.SkinPartCatalog
import cn.com.dcsgo.mihx.feature.user.UserSections
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P5 跨模块一致性测试。
 *
 * ## 为什么放在 :app 而不是 :core:skin 或 :feature:user
 *
 * - :core:skin 反向依赖 :feature:user 违反架构门禁
 * - :feature:user 看不到 :core:skin
 * - :app 两个都能看到,**最适合做跨模块一致性回归**
 *
 * ## 防的 bug 类型
 *
 * "UserSections.CUSTOM_SKIN 字符串改了但 SkinPartCatalog 没同步"(或反之)
 * → 描述合法但装配端不认 / 装配端认但描述校验拒, 与 L3 ROW_TEMPLATES
 * vs SONG_LIST_TEMPLATES 漂移是同一类陷阱。
 *
 * 负向对照: 临时把 SkinPartCatalog.CUSTOM_SKIN 改成 "skin-custom" → 1 条失败 → 还原恢复全绿。
 */
class UserSkinCatalogConsistencyTest {

    @Test
    fun `UserSections_CUSTOM_SKIN matches SkinPartCatalog_CUSTOM_SKIN exactly`() {
        assertEquals(
            "UserSections.CUSTOM_SKIN 必须与 SkinPartCatalog.CUSTOM_SKIN 字面相等 (两边都要改)",
            SkinPartCatalog.CUSTOM_SKIN,
            UserSections.CUSTOM_SKIN,
        )
    }
}
