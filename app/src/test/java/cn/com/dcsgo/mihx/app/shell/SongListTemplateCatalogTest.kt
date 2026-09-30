package cn.com.dcsgo.mihx.app.shell

import cn.com.dcsgo.mihx.core.skin.SkinPartCatalog
import cn.com.dcsgo.mihx.feature.playlist.SongListTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L3 行模板化 — 描述层白名单 ↔ 装配层 enum 一致性回归（2026-09-29）。
 *
 * ## 为什么放在 :app 而不是 :core:skin
 *
 * 描述里 `songList.template` 字段的合法值有两套"真相来源"：
 *  - [:core:skin] 的 [SkinPartCatalog.SONG_LIST_TEMPLATES]（白名单，校验器拒坏描述）
 *  - [cn.com.dcsgo.mihx.feature.playlist.SongListTemplate]（Kotlin 侧解析，运行时装配）
 *
 * :core:skin 与 :feature:playlist 是**两个独立模块**（架构门禁禁止 core 反向依赖 feature），
 * 所以交叉验证只能放在能看到双方的 :app。这是 :app 拿来做"装配"角色的应有之义。
 *
 * 两边一旦漂移（例如有人加新模板只加一边），会出现"描述合法但装配崩"或
 * "装配支持但描述校验拒"的诡异错位。本测试强制二者同步。
 *
 * ## 不在范围内
 *
 * - 校验器实际拒坏描述的行为 → SkinParserValidationTest 已覆盖
 * - 装配侧 grid 的实际渲染差异 → 真机验收覆盖,不在单元测试
 */
class SongListTemplateCatalogTest {

    @Test
    fun `catalog whitelist matches SongListTemplate enum exactly`() {
        val fromEnum = SongListTemplate.entries.map { it.id }.toSet()
        assertEquals(
            "SkinPartCatalog.SONG_LIST_TEMPLATES 必须与 SongListTemplate enum 逐项一致(添加新模板时两边都要改)",
            fromEnum,
            SkinPartCatalog.SONG_LIST_TEMPLATES,
        )
    }

    @Test
    fun `whitelist contains default and grid`() {
        // 锁死两个已知模板的存在,避免有人误删
        assert(SkinPartCatalog.SONG_LIST_TEMPLATES.contains("default"))
        assert(SkinPartCatalog.SONG_LIST_TEMPLATES.contains("grid"))
    }

    @Test
    fun `whitelist has no duplicates`() {
        val list = SkinPartCatalog.SONG_LIST_TEMPLATES.toList()
        assertEquals(list.size, list.distinct().size)
    }
}
