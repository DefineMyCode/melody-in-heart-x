package cn.com.dcsgo.mihx.domain.model

/**
 * 本地音乐列表排序方式。
 *
 * IMPORT_ORDER 为导入顺序（songId 升序，即自然顺序），其余字段按 collation 升降序。
 * 排序方向单独持久化（ascending），两种维度正交。
 */
enum class SongSortMode(val label: String) {
    IMPORT_ORDER("导入顺序"),
    TITLE("歌名"),
    ARTIST("歌手"),
    ALBUM("专辑"),
    DURATION("时长"),
    SAMPLE_RATE("采样率"),
    PLAY_COUNT("播放次数"),
    LAST_PLAYED("最近播放"),
}
