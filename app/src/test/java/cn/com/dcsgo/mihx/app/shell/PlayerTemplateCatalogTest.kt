package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.SkinPartCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L4 播放页形态 — 描述层白名单 ↔ 装配层 enum 一致性回归（2026-09-30）。
 *
 * 与 [SongListTemplateCatalogTest] 同款套路：
 * 两套真相来源必须在 [:app] 看得到的位置做交叉断言，避免"加新模板只加一处"的诡异错位。
 *
 * 范围：
 *  - catalog whitelist 与 enum 一致
 *  - 锁死已知模板（classic / vinyl / sumi）
 *  - 白名单无重复
 *
 * 不在范围：
 *  - 校验器拒坏描述的行为 → SkinParserValidationTest 已覆盖
 *  - 装配侧的实际渲染差异 → 真机验收覆盖
 */
class PlayerTemplateCatalogTest {

    @Test
    fun `catalog whitelist matches PlayerTemplate enum exactly`() {
        val fromEnum = PlayerTemplate.entries.map { it.id }.toSet()
        assertEquals(
            "SkinPartCatalog.PLAYER_TEMPLATES 必须与 PlayerTemplate enum 逐项一致(添加新模板时两边都要改)",
            fromEnum,
            SkinPartCatalog.PLAYER_TEMPLATES,
        )
    }

    @Test
    fun `whitelist contains classic, vinyl, sumi, and netease`() {
        // 锁死四个已知模板的存在,避免有人误删
        assert(SkinPartCatalog.PLAYER_TEMPLATES.contains("classic"))
        assert(SkinPartCatalog.PLAYER_TEMPLATES.contains("vinyl"))
        assert(SkinPartCatalog.PLAYER_TEMPLATES.contains("sumi"))
        assert(SkinPartCatalog.PLAYER_TEMPLATES.contains("netease"))
    }

    @Test
    fun `whitelist has no duplicates`() {
        val list = SkinPartCatalog.PLAYER_TEMPLATES.toList()
        assertEquals(list.size, list.distinct().size)
    }
}
