#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
情绪 V/A 改造方案的**离线模拟器**（零真机重扫，零风险）。

在本地拉下来的音频样本上模拟三步改造的组合效果，输出可量化的分布对比表：

  A  arousal → 绝对响度包络（RMS → dB → 固定映射）
  B  valence → 零点/斜率标定  v' = (v - B) / S
  C  锚点重标定（在 A/B 之后的输出分布上做 k-means + 1-1 匹配）

用法:
    python tools/emotion_sim.py --db melody.db --audio <音频目录> [--out report.md]

音频目录里放 `<songId>.<ext>` 形式的样本文件（用 adb 从设备拉取）。
投票逻辑复用 emotion_diag.py（与 core/model/EmotionGroup.kt 保持一致），
改那边要同步改这里。

判据见 docs/architecture/EMOTION_VA_UPGRADE.md §2。
"""
import argparse
import io
import json
import math
import os
import sqlite3
import subprocess
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emotion_diag import (  # noqa: E402
    AUTO, AUTO_ANCHOR, AUTO_IDX, GROUPS, HEADLINES, LOW_CONF, MAX_TAGS, MIN_RATIO,
    kmeans,
)

FFMPEG = "ffmpeg"
SR = 16000
WIN = 5.0
HOP = 2.5
W = int(WIN * SR)
H = int(HOP * SR)


# ── 音频 → 逐窗 RMS → dB ───────────────────────────────────────────────
def decode_db(path):
    """ffmpeg 解码成 16k mono float，按 5s 窗/2.5s hop 算 RMS → dB。

    窗口起点与 EmotionAnalyzer 的滑窗完全对齐（窗 i 起点 = i*40000 样本），
    因此可以和库里的 curveJson 逐窗配对。
    """
    p = subprocess.run(
        [FFMPEG, "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
        capture_output=True,
    )
    if p.returncode != 0 or not p.stdout:
        return None
    x = np.frombuffer(p.stdout, dtype=np.float32)
    if x.size < W:
        return None
    starts = np.arange(0, x.size - W + 1, H)
    out = np.empty(len(starts), dtype=np.float64)
    for i, s in enumerate(starts):
        seg = x[s:s + W].astype(np.float64)
        out[i] = np.sqrt((seg * seg).mean())
    return 20.0 * np.log10(out + 1e-6)


def map_db_to_arousal(db, floor, ceil):
    """dB → [-1,1] 的绝对响度映射（方案 A）。"""
    a = (db - floor) / (ceil - floor) * 2.0 - 1.0
    return np.clip(a, -1.0, 1.0)


# ── 投票（与 emotion_diag 一致，向量化）────────────────────────────────
def window_groups(v, a):
    """逐窗最近 auto 组下标（-1 = 近零弃权窗）。"""
    keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
    g = np.full(len(v), -1, dtype=np.int16)
    if keep.any():
        d = ((AUTO_ANCHOR[:, 0][None, :] - v[keep, None]) ** 2 +
             (AUTO_ANCHOR[:, 1][None, :] - a[keep, None]) ** 2)
        g[keep] = AUTO_IDX[d.argmin(axis=1)]
    return g


def _anchor_arrays(anchors):
    """返回 (av, aa, order)：order 把 auto 组下标映射到 GROUPS 下标。"""
    if anchors is not None:
        av = np.array([x[0] for x in anchors])
        aa = np.array([x[1] for x in anchors])
        order = [GROUPS.index(g) for g in AUTO]
    else:
        av, aa = AUTO_ANCHOR[:, 0], AUTO_ANCHOR[:, 1]
        order = list(AUTO_IDX)
    return av, aa, order


def group_hist(v, a, anchors=None):
    """给定锚点，统计逐窗落点分布。anchors 为 None 用默认锚点。"""
    av, aa, order = _anchor_arrays(anchors)
    keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
    cnt = np.zeros(len(GROUPS))
    if keep.any():
        d = ((av[None, :] - v[keep, None]) ** 2 + (aa[None, :] - a[keep, None]) ** 2)
        cnt[order] += np.bincount(d.argmin(axis=1), minlength=len(av))
    return cnt


def tags_of(v, a, anchors=None):
    """一首歌的展示词条（headline 列表）。"""
    av, aa, order = _anchor_arrays(anchors)
    keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
    if not keep.any():
        return []
    d = ((av[None, :] - v[keep, None]) ** 2 + (aa[None, :] - a[keep, None]) ** 2)
    win = d.argmin(axis=1)
    total = len(win)
    counts = np.bincount(win, minlength=len(av)).astype(np.float64)
    ratios = counts / total
    picked = []
    for gi in np.argsort(-ratios):
        if ratios[gi] < MIN_RATIO:
            break
        picked.append(int(gi))
        if len(picked) >= MAX_TAGS:
            break
    return [HEADLINES[order[i]] for i in picked]


MIN_SPACING = 0.25   # 组间最小间距（低于此值两个词条无法区分）
ANCHOR_BOUND = 0.95  # 锚点绝对值上限（越界=模型够不到）
MAX_SHIFT = 0.40     # 锚点相对原语义位置的最大位移（超出则该组保持原锚点）


def _match_clusters(centers, max_shift=None, undo=None):
    """簇心 ↔ 组 1-1 贪心匹配。

    max_shift: 允许的**语义漂移**上限；超过则该簇不参与匹配（该组保持原锚点）。
    undo: 把（已标定的）簇心还原到标定前坐标系的仿射变换 v0 = v*S + B。
          位移约束必须在**标定前**比较——否则 B 的全局平移会被误算成语义漂移，
          约束形同虚设、锚点重标定退化成 no-op。
    """
    pairs = []
    for j, c in enumerate(centers):
        c0 = undo(c) if undo is not None else c
        for gi in AUTO_IDX:
            d = float(np.hypot(c0[0] - GROUPS[gi][1], c0[1] - GROUPS[gi][2]))
            if max_shift is not None and d > max_shift:
                continue
            pairs.append((d, j, int(gi)))
    pairs.sort()
    used_c, used_g, match = set(), set(), {}
    for _, j, gi in pairs:
        if j in used_c or gi in used_g:
            continue
        used_c.add(j)
        used_g.add(gi)
        match[gi] = j
    return match


def enforce_constraints(anchors, min_spacing=MIN_SPACING, bound=ANCHOR_BOUND):
    """施加硬约束：锚点落在 [-bound, bound]，组间最小间距 >= min_spacing。"""
    pts = np.array(anchors, dtype=np.float64).copy()
    pts = np.clip(pts, -bound, bound)
    for _ in range(300):
        moved = False
        for i in range(len(pts)):
            for j in range(i + 1, len(pts)):
                d = pts[j] - pts[i]
                dist = float(np.linalg.norm(d))
                if dist < min_spacing:
                    if dist < 1e-6:
                        d = np.array([1.0, 0.0])
                        dist = 1.0
                    push = (min_spacing - dist) / 2.0 * (d / dist)
                    pts[i] -= push
                    pts[j] += push
                    moved = True
        pts = np.clip(pts, -bound, bound)
        if not moved:
            break
    return [(float(p[0]), float(p[1])) for p in pts]


def data_centers(v, a):
    """输出分布自身的 9 个簇中心（与组 1-1 匹配）——锚点"应该"在哪。"""
    keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
    if keep.sum() < 50:
        return None
    pts = np.column_stack([v[keep], a[keep]])
    centers, _ = kmeans(pts, len(AUTO), seed=7)
    pairs = []
    for j, c in enumerate(centers):
        for gi in AUTO_IDX:
            pairs.append((float(np.hypot(c[0] - GROUPS[gi][1], c[1] - GROUPS[gi][2])), j, int(gi)))
    pairs.sort()
    used_c, used_g, match = set(), set(), {}
    for _, j, gi in pairs:
        if j in used_c or gi in used_g:
            continue
        used_c.add(j)
        used_g.add(gi)
        match[gi] = j
        if len(match) == len(AUTO):
            break
    return [(float(centers[match[gi]][0]), float(centers[match[gi]][1])) for gi in AUTO_IDX]


def recalibrate_anchors(v, a, iters=3, max_shift=MAX_SHIFT, undo=None):
    """方案 C：在输出分布上 k-means + 1-1 匹配 + 硬约束 + 位移约束。

    位移约束是关键：簇心必须离该组的**原语义锚点**足够近，否则该组保持原锚点。
    语料里若根本不存在「高 V + 低 A」这种组合（本库就是这样：安静的歌都偏伤感），
    无约束匹配会把「治愈」这类词条配到高能量簇上——语义直接崩坏。
    undo: 标定前坐标系的还原变换（见 _match_clusters）。
    """
    orig = [(g[1], g[2]) for g in AUTO]
    cur = list(orig)
    for _ in range(iters):
        keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
        if keep.sum() < 50:
            break
        pts = np.column_stack([v[keep], a[keep]])
        centers, _ = kmeans(pts, len(AUTO), seed=7)
        match = _match_clusters(centers, max_shift, undo)
        if not match:
            break
        cur = [orig[k] if gi not in match
               else (float(centers[match[gi]][0]), float(centers[match[gi]][1]))
               for k, gi in enumerate(AUTO_IDX)]
    return enforce_constraints(cur)


def half_split_agreement(songs, anchors):
    """半曲一致性: 前半段与后半段各自投票, top1 相同的比例（随机基线 1/9≈11.1%）。"""
    agree = []
    for s in songs:
        v, a = s["v"], s["a"]
        mid = len(v) // 2
        t1 = tags_of(v[:mid], a[:mid], anchors)
        t2 = tags_of(v[mid:], a[mid:], anchors)
        agree.append(1.0 if (t1 and t2 and t1[0] == t2[0]) else 0.0)
    return float(np.mean(agree)) * 100 if agree else float("nan")


def anchor_offsets(anchors, centers):
    """锚点与"数据自身簇中心"的偏移（与是否重标定无关——两行都能比较）。"""
    if centers is None:
        return [float("nan")] * len(anchors)
    return [float(np.hypot(anchors[k][0] - centers[k][0], anchors[k][1] - centers[k][1]))
            for k in range(len(anchors))]


def min_spacing_of(anchors):
    m = float("inf")
    for i in range(len(anchors)):
        for j in range(i + 1, len(anchors)):
            m = min(m, float(np.hypot(anchors[i][0] - anchors[j][0],
                                      anchors[i][1] - anchors[j][1])))
    return m


def bootstrap_stability(songs, anchors, B=120, seed=7):
    """对窗口有放回重采样，top1 词条不变率。songs: [{'v':..,'a':..}]"""
    rng = np.random.default_rng(seed)
    av, aa, order = _anchor_arrays(anchors)
    stable = []
    for s in songs:
        v, a = s["v"], s["a"]
        base = tags_of(v, a, anchors)
        if not base:
            continue
        n = len(v)
        keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
        g = np.full(n, -1, dtype=np.int16)
        X = np.zeros((n, len(GROUPS)))
        if keep.any():
            d = ((av[None, :] - v[keep, None]) ** 2 + (aa[None, :] - a[keep, None]) ** 2)
            g[keep] = d.argmin(axis=1)
        for i in range(n):
            if g[i] >= 0:
                X[i, order[g[i]]] = 1.0
        idx = rng.integers(0, n, size=(B, n))
        counts = X[idx].sum(axis=1)
        tot = counts.sum(axis=1, keepdims=True)
        with np.errstate(invalid="ignore", divide="ignore"):
            ratios = np.where(tot > 0, counts / np.maximum(tot, 1), 0.0)
        hit = 0
        for b in range(B):
            r = ratios[b]
            picked = []
            for gi in np.argsort(-r):
                if r[gi] < MIN_RATIO:
                    break
                picked.append(int(gi))
                if len(picked) >= MAX_TAGS:
                    break
            if picked and HEADLINES[picked[0]] == base[0]:
                hit += 1
        stable.append(hit / B)
    return float(np.mean(stable)) if stable else float("nan")


def artist_icc(song_ids, artists, values):
    """艺人级 ICC（单因素方差分解）。"""
    groups = collections.defaultdict(list)
    for sid, art, val in zip(song_ids, artists, values):
        groups[art].append(val)
    gs = [np.asarray(v, dtype=np.float64) for v in groups.values() if len(v) >= 2]
    if len(gs) < 2:
        return float("nan")
    allv = np.concatenate(gs)
    grand, n, k = allv.mean(), len(allv), len(gs)
    ssb = sum(len(g) * (g.mean() - grand) ** 2 for g in gs)
    ssw = sum(((g - g.mean()) ** 2).sum() for g in gs)
    dfb, dfw = k - 1, n - k
    msb, msw = ssb / dfb, ssw / dfw
    n0 = (n - sum(len(g) ** 2 for g in gs) / n) / dfw
    return float((msb - msw) / (msb + (n0 - 1) * msw))


import collections  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True)
    ap.add_argument("--audio", required=True)
    ap.add_argument("--out", default="")
    ap.add_argument("--markdown", action="store_true", help="输出 markdown 表格")
    ap.add_argument("--best", default="A7+B1+C", help="要展开细节的方案名")
    ap.add_argument("--full-library", action="store_true",
                    help="无音频样本的歌也纳入（用模型自身 arousal），用于全库 B/C 分析")
    ap.add_argument("--only-no-a", action="store_true", help="只跑不含 A 的方案")
    ap.add_argument("--db-cache", default="",
                    help="app 精确路径的 dB 缓存(JSON: {songId: [dB...]})，由 tools 侧生成")
    args = ap.parse_args()

    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    con = sqlite3.connect(args.db)
    con.row_factory = sqlite3.Row
    try:
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    except Exception:
        pass

    db_cache = None
    if args.db_cache and os.path.exists(args.db_cache):
        with io.open(args.db_cache, "r", encoding="utf-8") as fp:
            db_cache = json.load(fp)
        print(f"载入 dB 缓存: {args.db_cache}（{len(db_cache)} 首，app 精确路径）")

    rows = con.execute("""
        SELECT e.songId, e.curveJson, e.valence, e.arousal, s.artist, s.title
        FROM song_emotions e JOIN songs s ON s.id = e.songId
        WHERE e.windowsAnalyzed > 0
    """).fetchall()
    meta = {r["songId"]: (r["artist"], r["title"]) for r in rows}

    # ── 载入音频样本并与库里的曲线配对 ──
    songs = []
    skipped = 0
    for sid, (artist, title) in meta.items():
        cand = None
        for ext in ("flac", "mp3", "m4a", "wav"):
            fp = os.path.join(args.audio, f"{sid}.{ext}")
            if os.path.exists(fp):
                cand = fp
                break
        if cand is None and not args.full_library and not (
                db_cache is not None and str(sid) in db_cache):
            continue
        curve = json.loads(next(r["curveJson"] for r in rows if r["songId"] == sid))
        n = len(curve) // 2
        v = np.array([curve[i * 2] for i in range(n)], dtype=np.float64)
        a = np.array([curve[i * 2 + 1] for i in range(n)], dtype=np.float64)
        if cand is None and db_cache is not None and str(sid) in db_cache:
            curve = json.loads(next(r["curveJson"] for r in rows if r["songId"] == sid))
            n = len(curve) // 2
            v = np.array([curve[i * 2] for i in range(n)], dtype=np.float64)
            a = np.array([curve[i * 2 + 1] for i in range(n)], dtype=np.float64)
            db = np.asarray(db_cache[str(sid)], dtype=np.float64)
            m = min(len(db), len(v))
            songs.append({"id": sid, "artist": artist, "title": title,
                          "v": v[:m], "a": a[:m], "db": db[:m]})
            continue
        if cand is None:
            # --full_library: 无音频样本时用模型自身的 arousal 占位（仅供 B/C 全库分析）
            songs.append({
                "id": sid, "artist": artist, "title": title,
                "v": v, "a": a, "db": None,
            })
            continue
        if db_cache is not None and str(sid) in db_cache:
            db = np.asarray(db_cache[str(sid)], dtype=np.float64)
        else:
            db = decode_db(cand)
        if db is None:
            skipped += 1
            continue
        m = min(len(db), len(v))
        if m < 4:
            skipped += 1
            continue
        songs.append({
            "id": sid, "artist": artist, "title": title,
            "v": v[:m], "a": a[:m], "db": db[:m],
        })

    print("=" * 78)
    print("  情绪 V/A 改造离线模拟")
    print("=" * 78)
    print(f"音频样本: {len(songs)} 首（跳过 {skipped}）")
    if not songs:
        print("没有可用样本。")
        return 1

    dbs = [s["db"] for s in songs if s["db"] is not None]
    all_db = np.concatenate(dbs) if dbs else np.array([0.0])
    print(f"真实 dB 分布: min={all_db.min():.1f} p5={np.percentile(all_db, 5):.1f} "
          f"median={np.median(all_db):.1f} p95={np.percentile(all_db, 95):.1f} "
          f"max={all_db.max():.1f}")

    # ── 方案矩阵 ──
    A_CANDS = {
        "A1 (-60..0dB)": (-60.0, 0.0),
        "A2 (-45..0dB)": (-45.0, 0.0),
        "A3 (-40..-5dB)": (-40.0, -5.0),
        "A5 (-35..-5dB)": (-35.0, -5.0),
        "A6 (-40..0dB)": (-40.0, 0.0),
        "A7 (-30..-5dB)": (-30.0, -5.0),
        "A4 (自适应 p5..p95)": (float(np.percentile(all_db, 5)), float(np.percentile(all_db, 95))),
    }
    B_CANDS = {
        "B1 (0.25,0.85)": (0.25, 0.85),
        "B2 (0.25,1.00)": (0.25, 1.00),
        "B3 (0.30,0.70)": (0.30, 0.70),
        "B4 (0.00,1.00)": (0.00, 1.00),
    }

    def evaluate(a_name, b_name, do_c, shift=MAX_SHIFT):
        """返回一组方案的指标。"""
        if a_name is None:
            arousal = np.concatenate([s["a"] for s in songs])
            per_song_a = {s["id"]: s["a"] for s in songs}
        else:
            floor, ceil = A_CANDS[a_name]
            per_song_a = {
                s["id"]: (map_db_to_arousal(s["db"], floor, ceil) if s["db"] is not None
                          else s["a"])
                for s in songs
            }
        if b_name is None:
            B, S = 0.0, 1.0
            per_song_v = {s["id"]: s["v"] for s in songs}
        else:
            B, S = B_CANDS[b_name]
            # 与 EmotionAnalyzer.calibrateValence 一致: 标定后 clamp 回 [-1,1]
            per_song_v = {
                s["id"]: np.clip((s["v"] - B) / S, -1.0, 1.0) for s in songs
            }

        allv = np.concatenate([per_song_v[s["id"]] for s in songs])
        alla = np.concatenate([per_song_a[s["id"]] for s in songs])

        if do_c:
            # 位移约束在"标定前"坐标系里比较: undo 把标定后的簇心还原回去
            undo = (lambda c: (c[0] * S + B, c[1])) if b_name is not None else None
            anchors = recalibrate_anchors(allv, alla, max_shift=shift, undo=undo)
        else:
            anchors = enforce_constraints([(g[1], g[2]) for g in AUTO])

        hist = group_hist(allv, alla, anchors)
        hist_n = hist / max(hist.sum(), 1)
        # 负面组（anchorV<0）窗占比
        neg = sum(hist_n[i] for i, gi in enumerate(AUTO_IDX) if GROUPS[gi][1] < 0)
        # 极差：各 auto 组占比 max/min（>0 的组）
        auto_h = np.array([hist_n[i] for i in AUTO_IDX])
        nz = auto_h[auto_h > 0]
        spread = float(auto_h.max() / nz.min()) if nz.size else float("nan")
        # 偏移：锚点 vs 数据自身簇中心（与是否重标定无关）
        centers = data_centers(allv, alla)
        offs = anchor_offsets(anchors, centers)

        # 词条级：每首 top1 词的分布（判断"燃"等词条够不够歌）
        top1 = []
        for s in songs:
            t = tags_of(per_song_v[s["id"]], per_song_a[s["id"]], anchors)
            top1.append(t[0] if t else None)
        cnt = collections.Counter(t for t in top1 if t)
        n_songs = len(songs)
        cold = min(cnt.values()) if cnt else 0

        # 稳定率 + ICC
        sim_songs = [{"v": per_song_v[s["id"]], "a": per_song_a[s["id"]]} for s in songs]
        stab = bootstrap_stability(sim_songs, anchors)
        half = half_split_agreement(sim_songs, anchors)
        icc_v = artist_icc([s["id"] for s in songs], [s["artist"] for s in songs],
                           [float(per_song_v[s["id"]].mean()) for s in songs])
        icc_a = artist_icc([s["id"] for s in songs], [s["artist"] for s in songs],
                           [float(per_song_a[s["id"]].mean()) for s in songs])

        return {
            "a": a_name or "现状",
            "b": b_name or "不标定",
            "c": "重标定" if do_c else "不重标定",
            "燃可达%": float((alla >= 0.6).mean() * 100),
            "低能可达%": float((alla <= -0.5).mean() * 100),
            "V均值": float(allv.mean()),
            "负面组%": float(neg * 100),
            "偏移max": float(max(offs)) if offs and not math.isnan(offs[0]) else float("nan"),
            "词条极差": spread,
            "冷门词条": cold,
            "最小间距": min_spacing_of(anchors),
            "稳定率%": stab * 100,
            "半曲一致%": half,
            "ICC_V": icc_v,
            "ICC_A": icc_a,
            "anchors": anchors,
            "top1": cnt,
            "n": n_songs,
        }

    print("\n" + "=" * 78)
    print("  方案矩阵（判据见 docs/architecture/EMOTION_VA_UPGRADE.md §2）")
    print("=" * 78)
    hdr = ["方案", "燃可达%", "低能可达%", "V均值", "负面组%", "偏移max", "词条极差", "冷门词条", "最小间距", "稳定率%", "半曲一致%", "ICC_V"]
    rows_out = []
    combos = [
        ("现状", None, None, False),
        ("不A+B1+C", None, "B1 (0.25,0.85)", True),
        ("不A+B1+不C", None, "B1 (0.25,0.85)", False),
        ("不A+不B+C", None, None, True),
        ("不A+B2+C", None, "B2 (0.25,1.00)", True),
        ("不A+B3+C", None, "B3 (0.30,0.70)", True),
        ("不A+B4+C", None, "B4 (0.00,1.00)", True),
        ("不A+不B+C(约束0.40)", None, None, True, 0.40),
        ("不A+不B+C(约束0.60)", None, None, True, 0.60),
        ("不A+不B+C(约束0.80)", None, None, True, 0.80),
        ("不A+不B+C(约束1.20)", None, None, True, 1.20),
        ("A1+B1+C", "A1 (-60..0dB)", "B1 (0.25,0.85)", True),
        ("A1+B1 (不C)", "A1 (-60..0dB)", "B1 (0.25,0.85)", False),
        ("A1+不B+C", "A1 (-60..0dB)", None, True),
        ("A1+不B+不C", "A1 (-60..0dB)", None, False),
        ("A2+B1+C", "A2 (-45..0dB)", "B1 (0.25,0.85)", True),
        ("A3+B1+C", "A3 (-40..-5dB)", "B1 (0.25,0.85)", True),
        ("A4+B1+C", "A4 (自适应 p5..p95)", "B1 (0.25,0.85)", True),
        ("A5+B1+C", "A5 (-35..-5dB)", "B1 (0.25,0.85)", True),
        ("A6+B1+C", "A6 (-40..0dB)", "B1 (0.25,0.85)", True),
        ("A7+B1+C", "A7 (-30..-5dB)", "B1 (0.25,0.85)", True),
        ("A3+B2+C", "A3 (-40..-5dB)", "B2 (0.25,1.00)", True),
        ("A7+B1+C(无位移约束)", "A7 (-30..-5dB)", "B1 (0.25,0.85)", True, None),
        ("A7+B1+C(约束0.60)", "A7 (-30..-5dB)", "B1 (0.25,0.85)", True, 0.60),
        ("A7+B1+C(约束0.25)", "A7 (-30..-5dB)", "B1 (0.25,0.85)", True, 0.25),
        ("A4+B1+C(约束0.25)", "A4 (自适应 p5..p95)", "B1 (0.25,0.85)", True, 0.25),
        ("A7+不B+C", "A7 (-30..-5dB)", None, True),
        ("A7+不B+不C", "A7 (-30..-5dB)", None, False),
        ("A7+B1+不C", "A7 (-30..-5dB)", "B1 (0.25,0.85)", False),
        ("A1+B2+C", "A1 (-60..0dB)", "B2 (0.25,1.00)", True),
        ("A1+B3+C", "A1 (-60..0dB)", "B3 (0.30,0.70)", True),
        ("A1+B4+C", "A1 (-60..0dB)", "B4 (0.00,1.00)", True),
    ]
    print(f"  {'方案':<16}" + "".join(f"{h:>11}" for h in hdr[1:]))
    for item in combos:
        name, a, b, c = item[:4]
        if args.only_no_a and a is not None:
            continue
        shift = item[4] if len(item) > 4 else MAX_SHIFT
        r = evaluate(a, b, c, shift)
        r["name"] = name
        rows_out.append(r)
        print(f"  {name:<16}" + "".join(f"{r[h]:>11.3f}" for h in hdr[1:]))

    # 详细：推荐方案
    _rec = [r for r in rows_out if r["name"] == args.best]
    if not _rec:
        con.close()
        return 0
    rec = _rec[0]
    print("\n" + "=" * 78)
    print(f"  方案 {args.best} 详情")
    print("=" * 78)
    print("  新锚点表（auto 组，按匹配顺序）:")
    for k, gi in enumerate(AUTO_IDX):
        g = GROUPS[gi]
        print(f"    {g[3]:<6} ({g[1]:+.2f},{g[2]:+.2f})  →  ({rec['anchors'][k][0]:+.3f},{rec['anchors'][k][1]:+.3f})")
    print("\n  top1 词条分布（每首歌的首个词条）:")
    tot = sum(rec["top1"].values())
    for w, c in rec["top1"].most_common():
        print(f"    {w:<6} {c:>4} 首 ({c / rec['n'] * 100:5.1f}%)")
    print(f"\n  ICC_V={rec['ICC_V']:+.3f}  ICC_A={rec['ICC_A']:+.3f}")
    print("  注: 样本内每艺人仅 1-2 首，ICC_V 严重低估，不可与全库 0.661 直接比较；")
    print("      ICC_A(RMS 响度) 有比较意义 —— 上升=模型在加强『认人』")

    if args.out:
        with io.open(args.out, "w", encoding="utf-8") as f:
            f.write("# 情绪 V/A 改造离线模拟结果\n\n")
            f.write(f"样本: {len(songs)} 首（真实音频解码）\n\n")
            f.write("| " + " | ".join(hdr) + " |\n")
            f.write("|" + "---|" * len(hdr) + "\n")
            for r in rows_out:
                f.write("| " + " | ".join([r["name"]] + [f"{r[h]:.3f}" for h in hdr[1:]]) + " |\n")
            f.write("\n## 推荐方案 A1+B1+C 的新锚点\n\n")
            f.write("| 组 | 当前锚点 | 新锚点 |\n|---|---|---|\n")
            for k, gi in enumerate(AUTO_IDX):
                g = GROUPS[gi]
                f.write(f"| {g[3]} | ({g[1]:+.2f}, {g[2]:+.2f}) | "
                        f"({rec['anchors'][k][0]:+.3f}, {rec['anchors'][k][1]:+.3f}) |\n")
        print(f"\n  已写出: {args.out}")

    con.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
