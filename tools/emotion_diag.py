#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
情绪模型离线诊断（零人工标注）。

从设备端拉下来的 melody.db 读 song_emotions 的逐窗曲线，纯离线复算
EmotionGroup.categoriesFor 的投票逻辑，输出四类诊断：

  1. 模型输出分布     —— valence/arousal 直方图、未定率、逐窗弃权率、V-A 相关（头塌缩判据）
  2. 零标签可信度     —— Bootstrap 稳定性、半曲一致性（对比随机基线 1/9）
  3. 词条分布         —— 展示词条/组 的集中度（分类退化判据）
  4. 人工标注样本     —— 有 userTags 的歌，模型词条 vs 用户词条的一致率（样本少时仅供参考）

用法:
    python tools/emotion_diag.py <melody.db> [--bootstrap 200] [--seed 7]

拉库:
    adb exec-out run-as cn.com.dcsgo.mihx.debug cat databases/melody.db > melody.db
    （WAL 未 checkpoint 时连 melody.db-wal / melody.db-shm 一起拉，sqlite 会自动回放）

注意: 投票逻辑必须与 core/model/EmotionGroup.kt 的 categoriesFor 保持一致，
      改那边就要同步改这里（本脚本内 GROUP 常量即其镜像）。
"""
import argparse
import collections
import json
import os
import sqlite3
import sys

import numpy as np

# ── EmotionGroup 镜像（core/model/EmotionGroup.kt）────────────────────────
# (anchorV, anchorA, headline, auto) —— 顺序与 Kotlin enum 一致
# 2026-10-25: 锚点已按 A/B 改造后的新坐标系在全库真值上重标定（见 §10 方案 C）
GROUPS = [
    ("BRIGHT", 0.486, 0.492, "元气", True),
    ("RAGE", 0.009, 0.607, "燃", True),
    ("EPIC", 0.228, 0.277, "神圣", True),
    ("WITTY", 0.2, 0.3, "鬼畜", False),   # auto=false: 不参与自动投票
    ("LOVE", 0.431, -0.124, "治愈", True),
    ("STILL", 0.078, -0.733, "静谧", True),
    ("SAD", -0.744, -0.432, "emo", True),
    ("ANXIOUS", -0.769, 0.590, "躁", True),
    ("CHILL", 0.012, -0.310, "放松", True),
    ("ABYSS", -0.774, -0.104, "窒息", True),
]
LOW_CONF = 0.15        # SongEmotion.LOW_CONF
MIN_RATIO = 0.15       # categoriesFor(minRatio)
MAX_TAGS = 4           # categoriesFor(topN)

CUR_MODEL_VERSION = "yamnet-va-v4-rms"

# 改造前(v3 及更早)的锚点表 —— 旧曲线在**旧坐标系**里, 必须配旧锚点投票。
# 顺序与 GROUPS 完全一致(组序相同 → cell 下标含义相同, 可混合统计)。
LEGACY_GROUPS = [
    ("BRIGHT", 0.6, 0.4, "元气", True),
    ("RAGE", 0.3, 0.85, "燃", True),
    ("EPIC", 0.2, 0.5, "神圣", True),
    ("WITTY", 0.2, 0.3, "鬼畜", False),
    ("LOVE", 0.6, -0.1, "治愈", True),
    ("STILL", 0.3, -0.55, "静谧", True),
    ("SAD", -0.5, -0.4, "emo", True),
    ("ANXIOUS", -0.4, 0.6, "躁", True),
    ("CHILL", 0.4, -0.3, "放松", True),
    ("ABYSS", -0.7, -0.2, "窒息", True),
]


def groups_for(model_version):
    """曲线所属坐标系 → 锚点表(组序与 GROUPS 一致)。"""
    return GROUPS if model_version == CUR_MODEL_VERSION else LEGACY_GROUPS


def anchor_array(groups):
    """锚点表 → auto 组坐标数组 (9, 2)。"""
    idx = [i for i, g in enumerate(groups) if g[4]]
    return np.array([[groups[i][1], groups[i][2]] for i in idx])

# ── 改造前(A/B 之前)的原始语义锚点 —— 冻结引用 ──────────────────────────
# 2026-10-25 起 EmotionGroup 的锚点已按新坐标系重算，但"语义蓝图"仍以这张表为准:
# 锚点重标定工具(anchor_fit/anchor_opt)需要它来做"旧坐标系 → 新坐标系"的映射与
# 语义序约束。它**不再**跟随 GROUPS 变化, 改 GROUPS 时不要动这里。
PRE_VA_ANCHORS = {
    "元气": (0.6, 0.4),
    "燃": (0.3, 0.85),
    "神圣": (0.2, 0.5),
    "鬼畜": (0.2, 0.3),
    "治愈": (0.6, -0.1),
    "静谧": (0.3, -0.55),
    "emo": (-0.5, -0.4),
    "躁": (-0.4, 0.6),
    "放松": (0.4, -0.3),
    "窒息": (-0.7, -0.2),
}

AUTO = [g for g in GROUPS if g[4]]
AUTO_IDX = np.array([i for i, g in enumerate(GROUPS) if g[4]])
ANCHOR = np.array([[g[1], g[2]] for g in GROUPS])          # (10, 2)
AUTO_ANCHOR = ANCHOR[AUTO_IDX]                             # (9, 2)
AUTO_HEADLINES = [GROUPS[i][3] for i in AUTO_IDX]
HEADLINES = [g[3] for g in GROUPS]

RANDOM_BASELINE = 1.0 / len(AUTO)   # top1 随机基线 = 1/9 ≈ 11.1%


# ── 投票（categoriesFor 的向量化镜像）────────────────────────────────────
def nearest_group(v, a, anchor=None):
    """逐窗找最近的 auto 组下标；近零窗返回 -1（弃权）。"""
    anchor = AUTO_ANCHOR if anchor is None else anchor
    d = (anchor[:, 0] - v) ** 2 + (anchor[:, 1] - a) ** 2
    return int(np.argmin(d))


def curve_to_groups(curve, anchor=None):
    """曲线 -> (groups[], ) 每窗的 auto 组下标（-1 = 弃权窗）。

    anchor: 该曲线所属坐标系的锚点表(旧曲线必须用旧锚点, 否则坐标系错配)。
    """
    out = np.empty(len(curve), dtype=np.int16)
    for i, (v, a) in enumerate(curve):
        if abs(v) < LOW_CONF and abs(a) < LOW_CONF:
            out[i] = -1
        else:
            out[i] = AUTO_IDX[nearest_group(v, a, anchor)]
    return out


def sorted_group_order(ratios, group_index=None):
    """按占比降序排组下标; 占比相同时按**组声明序**升序。

    与 core/model/EmotionGroup.kt 的 categoriesFor 保持一致:
        sortedWith(compareByDescending { 占比 }.thenBy { ordinal })

    2026-10-25 起加了显式兜底。之前只靠 sortedByDescending, 而 votes 是
    HashMap —— 迭代序取决于枚举 identity hash, 跨进程不保证一致, 同一首歌
    的词条先后会随 App 重启而变。全库 1095 首里 92 首(8.4%)的展示集合存在
    比例并列, 其中 17 首的 top1 也并列(并列会决定标题词)。

    group_index: 每个下标对应的 GROUPS 组下标(用于按组声明序兜底);
                 缺省认为下标本身就是 GROUPS 下标。
    """
    r = np.asarray(ratios, dtype=np.float64)
    sec = np.arange(len(r)) if group_index is None else np.asarray(group_index)
    return np.lexsort((sec, -r))


def groups_to_tags(groups):
    """组下标序列 -> 展示词条列表（categoriesFor 的比例过滤 + topN）。"""
    valid = groups[groups >= 0]
    total = valid.size
    if total == 0:
        return []
    counts = np.bincount(valid, minlength=len(GROUPS)).astype(np.float64)
    ratios = counts / total
    picked = []
    for gi in sorted_group_order(ratios):
        if ratios[gi] < MIN_RATIO:
            break
        picked.append((int(gi), float(ratios[gi])))
        if len(picked) >= MAX_TAGS:
            break
    return picked


def tags_to_headlines(picked):
    return [HEADLINES[gi] for gi, _ in picked]


# ── 数据读取 ────────────────────────────────────────────────────────────
def load_songs(conn, model_version=None):
    sql = (
        "SELECT songId, valence, arousal, curveJson, peakSec, windowsAnalyzed, "
        "durationSec, modelVersion, userValence, userArousal, userTags "
        "FROM song_emotions WHERE windowsAnalyzed > 0"
    )
    params = ()
    if model_version:
        sql += " AND modelVersion = ?"
        params = (model_version,)
    rows = conn.execute(sql, params).fetchall()
    out = []
    for r in rows:
        try:
            arr = json.loads(r["curveJson"])
        except Exception:
            continue
        n = len(arr) // 2
        if n < 2:
            continue
        curve = [(float(arr[i * 2]), float(arr[i * 2 + 1])) for i in range(n)]
        out.append({
            "songId": r["songId"],
            "valence": float(r["valence"]),
            "arousal": float(r["arousal"]),
            "curve": curve,
            "modelVersion": r["modelVersion"] or "",
            "userTags": [t for t in (r["userTags"] or "").split(",") if t.strip()],
            "userValence": r["userValence"],
            "userArousal": r["userArousal"],
        })
    return out


def load_titles(conn):
    try:
        rows = conn.execute("SELECT id, title, artist, album FROM songs").fetchall()
        return {r["id"]: (r["title"], r["artist"], r["album"]) for r in rows}
    except Exception:
        return {}


# ── 统计工具 ────────────────────────────────────────────────────────────
def pearson(x, y):
    x = np.asarray(x, dtype=np.float64)
    y = np.asarray(y, dtype=np.float64)
    if x.size < 2:
        return float("nan")
    xm, ym = x - x.mean(), y - y.mean()
    d = math_sqrt(float((xm ** 2).sum()) * float((ym ** 2).sum()))
    return float((xm * ym).sum() / d) if d > 0 else float("nan")


def math_sqrt(x):
    return float(np.sqrt(x))


def rankdata(a):
    a = np.asarray(a, dtype=np.float64)
    order = a.argsort()
    ranks = np.empty(len(a), dtype=np.float64)
    ranks[order] = np.arange(1, len(a) + 1, dtype=np.float64)
    # 平均秩处理并列
    uniq, inv, cnt = np.unique(a, return_inverse=True, return_counts=True)
    sums = np.zeros(len(uniq))
    np.add.at(sums, inv, ranks)
    sums = sums / cnt
    return sums[inv]


def spearman(x, y):
    if len(x) < 2:
        return float("nan")
    return pearson(rankdata(x), rankdata(y))


def histogram(values, lo=-1.0, hi=1.0, bins=20, width=34):
    counts, edges = np.histogram(np.clip(values, lo, hi), bins=bins, range=(lo, hi))
    peak = max(counts.max(), 1)
    lines = []
    for i, c in enumerate(counts):
        bar = "#" * int(round(c / peak * width))
        lines.append(f"  [{edges[i]:+.2f},{edges[i+1]:+.2f}) {bar:<{width}} {c}")
    return lines


def pct(x, q):
    return float(np.percentile(np.asarray(x, dtype=np.float64), q))


# ── 主诊断 ──────────────────────────────────────────────────────────────
# ── k-means（numpy 自实现，用于锚点标定分析）──────────────────────────
def kmeans(x, k, iters=60, seed=0):
    """x: (n, d)。返回 (centers (k, d), labels (n,))，k-means++ 初始化。"""
    rng = np.random.default_rng(seed)
    n = x.shape[0]
    centers = np.empty((k, x.shape[1]), dtype=np.float64)
    centers[0] = x[rng.integers(n)]
    for i in range(1, k):
        d2 = ((x[:, None, :] - centers[None, :i, :]) ** 2).sum(axis=2).min(axis=1)
        total = d2.sum()
        if total <= 0:
            centers[i] = x[rng.integers(n)]
        else:
            centers[i] = x[rng.choice(n, p=d2 / total)]
    labels = np.zeros(n, dtype=np.int32)
    for _ in range(iters):
        d2 = ((x[:, None, :] - centers[None, :, :]) ** 2).sum(axis=2)
        new_labels = d2.argmin(axis=1)
        if np.array_equal(new_labels, labels):
            break
        labels = new_labels
        for j in range(k):
            m = labels == j
            if m.any():
                centers[j] = x[m].mean(axis=0)
    return centers, labels


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("db")
    ap.add_argument("--bootstrap", type=int, default=200)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--model", default="", help="只分析指定 modelVersion 的曲线")
    args = ap.parse_args()

    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    if not os.path.exists(args.db):
        print(f"找不到数据库: {args.db}")
        return 1

    con = sqlite3.connect(args.db)
    con.row_factory = sqlite3.Row
    try:
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    except Exception:
        pass

    songs = load_songs(con, args.model or None)
    titles = load_titles(con)
    total_songs = con.execute("SELECT COUNT(*) c FROM songs").fetchone()["c"]
    n_tagged = con.execute(
        "SELECT COUNT(*) c FROM song_emotions WHERE userTags IS NOT NULL AND userTags<>''"
    ).fetchone()["c"]

    print("=" * 66)
    print("  Melody 情绪模型诊断（零人工标注）")
    print("=" * 66)
    print(f"数据库      : {args.db}")
    print(f"曲库歌曲    : {total_songs}")
    print(f"已分析      : {len(songs)}（有曲线）")
    print(f"人工标记    : {n_tagged}")
    print()

    if not songs:
        print("没有可分析的曲线数据。")
        return 1

    # 逐窗预计算（旧曲线用旧锚点, 否则坐标系错配: valence 正偏会被当成"正面"）
    per_song = []
    all_v, all_a = [], []
    ver_counts = collections.Counter(s["modelVersion"] for s in songs)
    for s in songs:
        g = curve_to_groups(s["curve"], anchor_array(groups_for(s["modelVersion"])))
        per_song.append(g)
        all_v.extend(c[0] for c in s["curve"])
        all_a.extend(c[1] for c in s["curve"])
    all_v = np.asarray(all_v)
    all_a = np.asarray(all_a)
    if len(ver_counts) > 1:
        print(f"  曲线版本分布: {dict(ver_counts)}（混合坐标系，逐首按各自版本的锚点投票）")

    # ── 1. 输出分布 ──
    print("─" * 66)
    print("【1】模型输出分布")
    print("─" * 66)
    for axis, vals in (("valence", all_v), ("arousal", all_a)):
        print(f"\n  {axis} 逐窗分布  n={vals.size}  "
              f"min={vals.min():+.3f} max={vals.max():+.3f} "
              f"mean={vals.mean():+.3f} std={vals.std():.3f}")
        for line in histogram(vals):
            print(line)

    song_v = np.array([s["valence"] for s in songs])
    song_a = np.array([s["arousal"] for s in songs])
    r_p = pearson(song_v, song_a)
    r_s = spearman(song_v, song_a)
    print(f"\n  逐窗  V-A 相关: pearson={r_p:+.3f}  spearman={r_s:+.3f}")
    print(f"  歌级  V-A 相关: pearson={pearson(song_v, song_a):+.3f}  "
          f"spearman={spearman(song_v, song_a):+.3f}")
    print("  → 判读: |r|>0.8 = 两轴塌缩成一个维度（头只学到了一个方向）")

    # 未定率
    und = np.array([(abs(s["valence"]) < LOW_CONF and abs(s["arousal"]) < LOW_CONF)
                    for s in songs])
    print(f"\n  未定率（|V|<{LOW_CONF} 且 |A|<{LOW_CONF} 的歌占比）: "
          f"{und.mean() * 100:.1f}%  ({int(und.sum())}/{len(songs)})")
    print("  → 判读: >60% 说明输出挤在原点，多数歌拿不到有效象限")

    # 逐窗弃权率
    allg = np.concatenate(per_song)
    abst = float((allg < 0).mean())
    print(f"  逐窗弃权率（近零窗，votesFor 弃权）: {abst * 100:.1f}%  "
          f"(有效窗 {int((allg >= 0).sum())}/{allg.size})")
    print("  → 判读: 弃权率高 = 投票算法能用的信息少")

    # ── 2. 零标签可信度 ──
    print()
    print("─" * 66)
    print("【2】零标签可信度")
    print("─" * 66)

    rng = np.random.default_rng(args.seed)
    B = args.bootstrap
    top1_stable, set_exact, jacc, base_tags = [], [], [], []
    half_agree, half_jacc = [], []

    for s, g in zip(songs, per_song):
        n = len(g)
        base = tags_to_headlines(groups_to_tags(g))
        base_tags.append(base)

        # --- Bootstrap：对窗口有放回重采样 B 次，看展示词条稳不稳 ---
        valid_mask = g >= 0
        if valid_mask.sum() == 0:
            top1_stable.append(0.0)
            set_exact.append(0.0)
            jacc.append(0.0)
            half_agree.append(0.0)
            half_jacc.append(0.0)
            continue

        # one-hot（只对有效窗建，弃权窗权重 0）
        X = np.zeros((n, len(GROUPS)))
        for i in range(n):
            if g[i] >= 0:
                X[i, g[i]] = 1.0

        idx = rng.integers(0, n, size=(B, n))
        counts = X[idx].sum(axis=1)                      # (B, G)
        totals = counts.sum(axis=1, keepdims=True)
        with np.errstate(invalid="ignore", divide="ignore"):
            ratios = np.where(totals > 0, counts / np.maximum(totals, 1), 0.0)

        st, ex, jc = 0, 0, 0.0
        base_set = set(base)
        for b in range(B):
            r = ratios[b]
            picked, order = [], sorted_group_order(r)
            for gi in order:
                if r[gi] < MIN_RATIO:
                    break
                picked.append(int(gi))
                if len(picked) >= MAX_TAGS:
                    break
            tags = [HEADLINES[gi] for gi in picked]
            if not tags:
                continue
            if base and tags[0] == base[0]:
                st += 1
            ts = set(tags)
            if ts == base_set:
                ex += 1
            union = ts | base_set
            jc += len(ts & base_set) / len(union) if union else 0.0
        top1_stable.append(st / B)
        set_exact.append(ex / B)
        jacc.append(jc / B)

        # --- 半曲一致性：前后半段分别投票 ---
        mid = n // 2
        t1 = tags_to_headlines(groups_to_tags(g[:mid]))
        t2 = tags_to_headlines(groups_to_tags(g[mid:]))
        if t1 and t2:
            half_agree.append(1.0 if t1[0] == t2[0] else 0.0)
            s1, s2 = set(t1), set(t2)
            u = s1 | s2
            half_jacc.append(len(s1 & s2) / len(u) if u else 0.0)
        else:
            half_agree.append(0.0)
            half_jacc.append(0.0)

    ts = np.asarray(top1_stable)
    ex = np.asarray(set_exact)
    jc = np.asarray(jacc)
    ha = np.asarray(half_agree)
    hj = np.asarray(half_jacc)

    print(f"\n  Bootstrap 稳定性（B={B}，对窗口有放回重采样）")
    print(f"    top1 词条不变率 : 均值 {ts.mean() * 100:.1f}%  中位 {pct(ts, 50) * 100:.1f}%  "
          f"p10 {pct(ts, 10) * 100:.1f}%")
    print(f"    整组词条完全一致 : 均值 {ex.mean() * 100:.1f}%")
    print(f"    词条集 Jaccard   : 均值 {jc.mean():.3f}")
    print("    分布: " + "  ".join(
        f"{lo}-{hi}%:{int(((ts >= lo / 100) & (ts < hi / 100)).sum())}"
        for lo, hi in ((0, 30), (30, 60), (60, 80), (80, 101))))
    print("    → 判读: top1 稳定率 <30% = 展示的词条基本是掷骰子；>60% = 稳健属性")

    print(f"\n  半曲一致性（前半段 vs 后半段各自投票）")
    print(f"    top1 一致率      : {ha.mean() * 100:.1f}%   随机基线 {RANDOM_BASELINE * 100:.1f}%")
    print(f"    词条集 Jaccard   : {hj.mean():.3f}")
    print("    → 判读: 一致率≈基线 = 输出是噪声；显著高于基线 = 有稳定信号")

    # ── 3. 词条分布 ──
    print()
    print("─" * 66)
    print("【3】展示词条分布（分类退化判据）")
    print("─" * 66)
    from collections import Counter
    cnt = Counter()
    song_count = Counter()
    empty_songs = 0
    for tags in base_tags:
        if not tags:
            empty_songs += 1
        for t in set(tags):
            cnt[t] += 1
        song_count[len(tags)] += 1
    print(f"  无词条（未定）的歌: {empty_songs}/{len(songs)} "
          f"({empty_songs / len(songs) * 100:.1f}%)")
    print(f"  每首歌词条数分布: " + "  ".join(
        f"{k}词:{v}" for k, v in sorted(song_count.items())))
    print("\n  展示词条命中次数（按组代表词）:")
    for w, c in cnt.most_common():
        bar = "#" * int(round(c / max(cnt.values(), default=1) * 30))
        print(f"    {w:<6} {bar:<30} {c}")

    # 组级分布（所有窗）
    win_cnt = Counter()
    for g in per_song:
        for gi in g[g >= 0]:
            win_cnt[HEADLINES[gi]] += 1
    tot_win = sum(win_cnt.values())
    print("\n  逐窗落点分布（所有有效窗）:")
    for w, c in win_cnt.most_common():
        print(f"    {w:<6} {c / tot_win * 100:5.1f}%  ({c})")

    # ── 4. 人工标注样本 ──
    print()
    print("─" * 66)
    print(f"【4】人工标注样本（{n_tagged} 首）")
    print("─" * 66)
    tagged = [s for s in songs if s["userTags"]]
    if not tagged:
        print("  无人工标注样本 —— 命中率无法计算（符合预期：诊断不依赖标注）")
    else:
        hit = 0
        for idx_s, s in enumerate(songs):
            if not s["userTags"]:
                continue
            model_tags = set(base_tags[idx_s])
            user_tags = set(s["userTags"])
            inter = model_tags & user_tags
            if inter:
                hit += 1
            t = titles.get(s["songId"], ("", "", ""))
            print(f"  [{s['songId']}] {t[0]} — {t[1]}")
            print(f"      模型: {sorted(model_tags) or '（无/未定）'}")
            print(f"      用户: {sorted(user_tags)}")
            print(f"      交集: {sorted(inter) or '（无）'}")
        print(f"\n  命中率（用户词出现在模型词条中）: {hit}/{len(tagged)}")
        if len(tagged) < 20:
            print("  ⚠ 样本量 <20，命中率仅供个人参考，不代表总体准确率")
            print("    （且样本是「用户认为模型判错时才标记」的，天然有偏）")

    # ── 4b. 独立纠正识别 ──
    # 校准弹窗预勾选模型词条，用户点「确认」即 userTags == 模型词条 → 这不是独立标注。
    # 只有 userTags 与「重算的模型词条」不同的样本，才是用户真正纠正过的独立真值。
    print()
    print("  ── 独立纠正识别 ──")
    indep = []
    for idx_s, s in enumerate(songs):
        if not s["userTags"]:
            continue
        model_tags = set(base_tags[idx_s])
        if set(s["userTags"]) != model_tags:
            indep.append((s, model_tags, set(s["userTags"])))
    print(f"    人工标记样本            : {len(tagged)}")
    print(f"    其中与模型词条完全相同  : {len(tagged) - len(indep)}  ← 非独立标注（预勾选后直接确认）")
    print(f"    **独立纠正样本**        : {len(indep)}  ← 唯一可当真值用的")
    for s, mt, ut in indep:
        t = titles.get(s["songId"], ("", "", ""))
        print(f"      [{s['songId']}] {t[0]} — {t[1]}")
        print(f"          模型: {sorted(mt)}")
        print(f"          用户: {sorted(ut)}")
        print(f"          去掉: {sorted(mt - ut)}   加上: {sorted(ut - mt)}")
    if not indep:
        print("      （当前无独立纠正样本）")
        print("      → 结论: 现有 userTags 无法作为模型真值。校准弹窗预勾选模型词条，")
        print("        用户直接确认即写回同一组词。要收集真值需让用户「主动改词」，")
        print("        或在写入时记录 userTagsEdited 标志（区分采纳 vs 纠正）。")

    # ── 5. 曲库样本（外部数据集对齐预检）──
    print()
    print("─" * 66)
    print("【5】曲库样本（外部 V/A 数据集对齐预检）")
    print("─" * 66)
    sample = [(titles.get(s["songId"], ("?", "?", ""))) for s in songs[:15]]
    for t in sample:
        print(f"  {t[0][:40]:<42} | {t[1][:30]}")
    print(f"\n  共 {len(songs)} 首可对齐。若歌名/歌手为英文且能在 Jamendo 找到，")
    print("  可用 MTG-Jamendo 元数据(仅 34.9MB, 无需音频)自动对齐 mood/theme 标签 → 弱 V/A 真值。")

    # ── 6. 锚点标定分析 ──
    print()
    print("─" * 66)
    print("【6】锚点标定分析（valence 正偏 / 词条覆盖不均的诊断）")
    print("─" * 66)

    def window_histogram(anchors):
        """按给定锚点重算逐窗落点分布（与展示口径一致）。"""
        av = np.array([a[0] for a in anchors])
        aa = np.array([a[1] for a in anchors])
        cnt = np.zeros(len(anchors), dtype=np.int64)
        for s in songs:
            c = np.asarray(s["curve"], dtype=np.float64)
            v, a = c[:, 0], c[:, 1]
            keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
            if not keep.any():
                continue
            d = (av[None, :] - v[keep, None]) ** 2 + (aa[None, :] - a[keep, None]) ** 2
            cnt += np.bincount(d.argmin(axis=1), minlength=len(anchors))
        return cnt

    pts = np.array([[s["valence"], s["arousal"]] for s in songs], dtype=np.float64)
    centers, labels = kmeans(pts, len(AUTO), seed=args.seed)

    # 被分析曲线的"当前锚点"= 其所属坐标系对应的表（混合库取占多数的版本）
    active = groups_for(ver_counts.most_common(1)[0][0])

    # 1-1 贪心最优匹配：每个组锚点配一个簇中心（不允许两个簇抢同一组）
    pairs = []
    for j, c in enumerate(centers):
        for gi in AUTO_IDX:
            pairs.append((float(np.hypot(c[0] - active[gi][1], c[1] - active[gi][2])), j, int(gi)))
    pairs.sort()
    used_c, used_g, match = set(), set(), {}
    for dist, j, gi in pairs:
        if j in used_c or gi in used_g:
            continue
        used_c.add(j)
        used_g.add(gi)
        match[gi] = (j, dist)
        if len(match) == len(AUTO):
            break

    cur_anchors = [(g[1], g[2]) for g in active if g[4]]
    cur_hist = window_histogram(cur_anchors)
    new_anchors = []
    for gi in AUTO_IDX:
        j, _ = match[gi]
        new_anchors.append((float(centers[j][0]), float(centers[j][1])))
    new_hist = window_histogram(new_anchors)

    print(f"\n  对歌级 (meanV, meanA) 做 {len(AUTO)} 簇 k-means，与当前锚点 1-1 匹配:")
    print(f"  {'组':<6} {'当前锚点':<15} {'实际中心':<15} {'偏移':<7} "
          f"{'现锚点窗占比':<13} {'重标定后':<9}")
    for k, gi in enumerate(AUTO_IDX):
        g = active[gi]
        j, dist = match[gi]
        c = centers[j]
        print(f"  {g[3]:<6} ({g[1]:+.2f},{g[2]:+.2f})    ({c[0]:+.2f},{c[1]:+.2f})    "
              f"{dist:5.2f}   {cur_hist[k] / cur_hist.sum() * 100:5.1f}%         "
              f"{new_hist[k] / new_hist.sum() * 100:5.1f}%")
    print(f"\n  歌级 V 均值 {song_v.mean():+.3f} / A 均值 {song_a.mean():+.3f}  "
          f"(正值多 = 模型系统性正偏)")
    neg_cur = sum(cur_hist[k] for k, gi in enumerate(AUTO_IDX) if active[gi][1] < 0)
    neg_new = sum(new_hist[k] for k, gi in enumerate(AUTO_IDX) if active[gi][1] < 0)
    print(f"  负面组(V<0) 窗占比: 现锚点 {neg_cur / cur_hist.sum() * 100:.1f}%  →  "
          f"重标定 {neg_new / new_hist.sum() * 100:.1f}%")
    print("\n  → 判读: 偏移大 = 该组锚点与实际输出分布脱节（词条覆盖不均的根因）")
    print("    重标定是纯数学调整（改 EmotionGroup 的 anchorV/anchorA），不需要重训模型。")

    # ── 7. 艺人级 ICC：模型在测「歌」还是测「人/音色」──
    print()
    print("─" * 66)
    print("【7】艺人级 ICC —— 模型测的是情绪，还是「谁唱的」？")
    print("─" * 66)
    artist_rows = con.execute(
        "SELECT e.songId, e.valence, e.arousal, s.artist "
        "FROM song_emotions e JOIN songs s ON s.id = e.songId "
        "WHERE e.windowsAnalyzed > 0"
    ).fetchall()
    by_artist = {}
    for r in artist_rows:
        by_artist.setdefault(r["artist"], []).append((float(r["valence"]), float(r["arousal"])))

    def calc_icc(vals_by_artist, key):
        groups = [np.asarray([v[key] for v in vs], dtype=np.float64)
                  for vs in vals_by_artist.values() if len(vs) >= 2]
        if not groups:
            return float("nan"), 0, 0
        allv = np.concatenate(groups)
        grand, n, k = allv.mean(), len(allv), len(groups)
        ssb = sum(len(g) * (g.mean() - grand) ** 2 for g in groups)
        ssw = sum(((g - g.mean()) ** 2).sum() for g in groups)
        dfb, dfw = k - 1, n - k
        msb, msw = ssb / dfb, ssw / dfw
        n0 = (n - sum(len(g) ** 2 for g in groups) / n) / dfw
        return float((msb - msw) / (msb + (n0 - 1) * msw)), k, n

    for name, key in (("valence", 0), ("arousal", 1)):
        icc_v, k, n = calc_icc(by_artist, key)
        print(f"  {name}: ICC = {icc_v:+.3f}   "
              f"(多曲艺人 {k} 位 / {n} 首参与方差分解)")
    print("\n  → 判读: ICC 高 = 同一艺人的歌被模型给出相近分数 → 模型主要在识别")
    print("    「谁唱的」(音色/曲风/制作风格)，而非逐曲情绪；ICC 近 0 = 分数逐曲独立。")
    print("    这是 YAMNet 音频事件表征「音色主导」在生产模型自身输出上的直接测量。")

    # 单艺人内部离散度样例
    print("\n  多曲艺人的内部离散度（看模型能否区分同一人的不同歌）:")
    shown = 0
    for a, vs in sorted(by_artist.items(), key=lambda kv: -len(kv[1])):
        if len(vs) < 8:
            continue
        arr = np.asarray(vs, dtype=np.float64)
        print(f"    {a[:24]:<26} n={len(vs):3}  "
              f"V {arr[:, 0].min():+.2f}~{arr[:, 0].max():+.2f} (std {arr[:, 0].std():.2f})  "
              f"A {arr[:, 1].min():+.2f}~{arr[:, 1].max():+.2f} (std {arr[:, 1].std():.2f})")
        shown += 1
        if shown >= 6:
            break

    # 方差分解：窗内 vs 歌间
    print("\n  方差分解（valence / arousal）:")
    for name, key in (("valence", 0), ("arousal", 1)):
        win = np.array([c[key] for s in songs for c in s["curve"]], dtype=np.float64)
        song = np.array([s["valence"] if key == 0 else s["arousal"] for s in songs],
                        dtype=np.float64)
        within = np.mean([
            float(np.std([c[key] for c in s["curve"]])) ** 2 for s in songs])
        ratio = within / win.var() if win.var() > 0 else 0.0
        print(f"    {name}: 逐窗 std={win.std():.3f}  歌级 std={song.std():.3f}  "
              f"窗内方差占比={ratio:.1%}")
    print("    → 判读: 窗内占比高 = 同一首歌内逐窗有起伏（曲线有信息）；")
    print("      若接近 100% 则每首歌内部几乎不变，曲线是平的。")

    con.close()
    print()
    print("=" * 66)
    return 0


if __name__ == "__main__":
    sys.exit(main())
