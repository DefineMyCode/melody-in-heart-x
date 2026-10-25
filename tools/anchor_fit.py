#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
锚点重标定（方案 C）的**全库真值**实现。

背景:
  A(arousal→RMS 响度包络) + B(valence 零点/斜率标定) 改变了 V-A 坐标系的定义,
  原 `EmotionGroup` 的语义锚点因此脱节。本脚本用「改造前库 + 改造后库」配对,
  在全库真值上重建锚点:

  1. 经验映射  旧坐标系 → 新坐标系
       valence: 线性拟合 v_new = alpha*v_old + beta (理论值 alpha=1/0.85, beta=-0.25/0.85)
       arousal: 分箱单调映射 a_old → E[a_new | a_old]  (RMS 取代 VA 头, 无解析逆)
  2. 语义参考点  原锚点经上述映射搬到新坐标系 = 各组在新体系下的"应有位置"
  3. 锚点放置  Lloyd 定点迭代: 每组保留身份、只向自己的质心移动,
                移动量受 max_shift 约束(语义漂移上限) → 防止"治愈"被吸到高能量簇
  4. 硬约束    |anchor| <= 0.95, 组间最小间距 >= 0.25
  5. 评估      词条极差 / 冷门词条 / Bootstrap 稳定率 / 半曲一致性 / 艺人级 ICC

用法:
    python tools/anchor_fit.py --new-db <新库> --old-db <旧库> [--max-shift 0.40]
    python tools/anchor_fit.py --new-db <新库> --old-db <旧库> --out anchor_report.md

投票/评估逻辑全部 import 自 emotion_diag / emotion_sim, 与
core/model/EmotionGroup.kt 保持一致(那边改了这边要同步)。
"""
import argparse
import collections
import json
import math
import os
import sqlite3
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emotion_diag import AUTO, AUTO_IDX, GROUPS, LOW_CONF, PRE_VA_ANCHORS  # noqa: E402
from emotion_mirror import CURRENT_MODEL_VERSION, label_pattern  # noqa: E402
from emotion_sim import (  # noqa: E402
    artist_icc,
    bootstrap_stability,
    enforce_constraints,
    group_hist,
    half_split_agreement,
    min_spacing_of,
    tags_of,
)

MIN_SPACING = 0.25
ANCHOR_BOUND = 0.95
MAX_SHIFT = 0.40
NEW_MODEL = CURRENT_MODEL_VERSION

# 手工语义网格：在新坐标系里按语义蓝图重排的锚点候选。
# 语义蓝图（arousal 高→低）：
#   燃(能量最高)  神圣(中高能量/中性)  躁(中高能量/负面)
#   元气(高正面)  治愈(正面/中低能量)  放松(中性/低能量)
#   静谧(能量最低) emo(负面/低能量)   窒息(最负面)
# 顺序必须与 AUTO_IDX 一致（元气/燃/神圣/治愈/静谧/emo/躁/放松/窒息）。
HAND_GRID = [
    (0.52, 0.22),    # 元气
    (0.15, 0.72),    # 燃
    (0.05, 0.40),    # 神圣
    (0.48, -0.12),   # 治愈
    (-0.12, -0.78),  # 静谧
    (-0.58, -0.42),  # emo
    (-0.60, 0.55),   # 躁
    (0.15, -0.42),   # 放松
    (-0.85, -0.02),  # 窒息
]


# ── 读库 ───────────────────────────────────────────────────────────────
def load_curves(db, model_version=None):
    """→ {songId: (v数组, a数组)}，可选按 modelVersion 过滤。"""
    con = sqlite3.connect(db)
    try:
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    except Exception:
        pass
    sql = "SELECT songId, curveJson, modelVersion FROM song_emotions WHERE windowsAnalyzed > 0"
    if model_version:
        sql += " AND modelVersion LIKE ?"
        rows = con.execute(sql, (label_pattern(model_version),)).fetchall()
    else:
        rows = con.execute(sql).fetchall()
    out = {}
    for sid, cj, mv in rows:
        try:
            arr = json.loads(cj)
        except Exception:
            continue
        n = len(arr) // 2
        if n < 2:
            continue
        v = np.array([arr[i * 2] for i in range(n)], dtype=np.float64)
        a = np.array([arr[i * 2 + 1] for i in range(n)], dtype=np.float64)
        out[sid] = (v, a)
    con.close()
    return out


def load_meta(db):
    con = sqlite3.connect(db)
    con.row_factory = sqlite3.Row
    rows = con.execute(
        "SELECT e.songId, s.artist, s.title FROM song_emotions e "
        "JOIN songs s ON s.id = e.songId WHERE e.windowsAnalyzed > 0"
    ).fetchall()
    con.close()
    return {r["songId"]: (r["artist"], r["title"]) for r in rows}


# ── 经验映射 ───────────────────────────────────────────────────────────
def fit_linear(x, y):
    """y = alpha*x + beta（最小二乘）。"""
    alpha, beta = np.polyfit(x, y, 1)
    pred = alpha * x + beta
    resid = y - pred
    ss_res = float((resid ** 2).sum())
    ss_tot = float(((y - y.mean()) ** 2).sum())
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0 else float("nan")
    return float(alpha), float(beta), r2, float(resid.std())


def fit_monotone(x, y, bins=60):
    """分箱均值 → 单调化 → 线性插值，得到 x → E[y|x] 的单调映射。

    返回 (knots_x, knots_y)，用于 np.interp。
    """
    edges = np.linspace(-1.0, 1.0, bins + 1)
    idx = np.clip(np.digitize(x, edges) - 1, 0, bins - 1)
    kx, ky = [], []
    for b in range(bins):
        m = idx == b
        if not m.any():
            continue
        kx.append(float(x[m].mean()))
        ky.append(float(y[m].mean()))
    kx = np.array(kx)
    ky = np.array(ky)
    # 单调化（PAVA 的简化版：累积最大）
    ky = np.maximum.accumulate(ky)
    # 端点外推：用首/末箱的斜率补齐到 [-1,1]
    if len(kx) >= 2:
        lo_slope = (ky[1] - ky[0]) / max(kx[1] - kx[0], 1e-6)
        hi_slope = (ky[-1] - ky[-2]) / max(kx[-1] - kx[-2], 1e-6)
        if kx[0] > -1.0:
            kx = np.concatenate([[-1.0], kx])
            ky = np.concatenate([[ky[0] - lo_slope * (kx[1] - (-1.0))], ky])
        if kx[-1] < 1.0:
            kx = np.concatenate([kx, [1.0]])
            ky = np.concatenate([ky, [ky[-1] + hi_slope * (1.0 - kx[-2])]])
    return kx, ky


def map_valence(kx, ky, v):
    return float(np.interp(v, kx, ky))


def ref_by_quantile(old_v, new_v, old_a, new_a):
    """分位数映射：原锚点在**旧分布**里的分位 → 新分布的同一分位。

    为什么不用条件均值：RMS 响度的离散度远大于 VA 头 arousal，
    E[a_new|a_old] 会被回归稀释压扁（实测 [-0.17,+0.49]），
    拿它当"语义位置"等于把高能组拉到中段。
    分位数映射保留的是**相对位置**（"最响的那批"仍然是那批），这才是锚点的语义。
    """
    def q_of(dist, x):
        return float((dist < x).mean())

    ref = []
    for gi in AUTO_IDX:
        v0, a0 = PRE_VA_ANCHORS[GROUPS[gi][3]]
        pv = q_of(old_v, v0)
        pa = q_of(old_a, a0)
        ref.append((float(np.quantile(new_v, pv)), float(np.quantile(new_a, pa))))
    return ref


# ── Lloyd 定点锚点放置 ─────────────────────────────────────────────────
def place_anchors(songs, ref, max_shift=MAX_SHIFT, iters=40):
    """每组保留身份、只向自己的质心移动；单次移动量受 max_shift 约束。

    ref: 各 auto 组在数据坐标系下的语义参考点 [(v,a) × 9]
    返回 [(v,a) × 9]
    """
    cur = [tuple(p) for p in ref]
    av = np.array([p[0] for p in cur])
    aa = np.array([p[1] for p in cur])
    refv = np.array([p[0] for p in ref])
    refa = np.array([p[1] for p in ref])
    for _ in range(iters):
        sums = np.zeros((len(cur), 2))
        cnts = np.zeros(len(cur))
        for s in songs:
            v, a = s["v"], s["a"]
            keep = ~((np.abs(v) < LOW_CONF) & (np.abs(a) < LOW_CONF))
            if not keep.any():
                continue
            d = ((av[None, :] - v[keep, None]) ** 2 + (aa[None, :] - a[keep, None]) ** 2)
            g = d.argmin(axis=1)
            np.add.at(sums, g, np.column_stack([v[keep], a[keep]]))
            np.add.at(cnts, g, 1.0)
        new_av, new_aa = av.copy(), aa.copy()
        for k in range(len(cur)):
            if cnts[k] < 1:
                continue
            c = sums[k] / cnts[k]
            step_v = c[0] - refv[k]
            step_a = c[1] - refa[k]
            dist = math.hypot(step_v, step_a)
            if dist > max_shift and dist > 1e-9:
                step_v *= max_shift / dist
                step_a *= max_shift / dist
            new_av[k] = refv[k] + step_v
            new_aa[k] = refa[k] + step_a
        if np.allclose(new_av, av, atol=1e-6) and np.allclose(new_aa, aa, atol=1e-6):
            av, aa = new_av, new_aa
            break
        av, aa = new_av, new_aa
    return [(float(av[k]), float(aa[k])) for k in range(len(cur))]


# ── 评估 ───────────────────────────────────────────────────────────────
def evaluate(songs, anchors, meta):
    hist = group_hist(np.concatenate([s["v"] for s in songs]),
                      np.concatenate([s["a"] for s in songs]), anchors)
    hist_n = hist / max(hist.sum(), 1)
    auto_h = np.array([hist_n[i] for i in AUTO_IDX])
    nz = auto_h[auto_h > 0]
    spread = float(auto_h.max() / nz.min()) if nz.size else float("nan")

    top1 = []
    for s in songs:
        t = tags_of(s["v"], s["a"], anchors)
        top1.append(t[0] if t else None)
    cnt = collections.Counter(t for t in top1 if t)
    cold = min(cnt.values()) if cnt else 0

    sim = [{"v": s["v"], "a": s["a"]} for s in songs]
    stab = bootstrap_stability(sim, anchors)
    half = half_split_agreement(sim, anchors)
    ids = [s["id"] for s in songs]
    arts = [meta[s["id"]][0] for s in songs]
    icc_v = artist_icc(ids, arts, [float(s["v"].mean()) for s in songs])
    icc_a = artist_icc(ids, arts, [float(s["a"].mean()) for s in songs])

    neg = float(sum(hist_n[i] for i, gi in enumerate(AUTO_IDX) if GROUPS[gi][1] < 0) * 100)
    share = {}
    for k, gi in enumerate(AUTO_IDX):
        share[GROUPS[gi][3]] = float(hist_n[gi] * 100)
    return {
        "词条极差": spread,
        "冷门词条": cold,
        "稳定率%": stab * 100,
        "半曲一致%": half,
        "ICC_V": icc_v,
        "ICC_A": icc_a,
        "负面组%": neg,
        "最小间距": min_spacing_of(anchors),
        "top1": cnt,
        "share": share,
        "n": len(songs),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--new-db", required=True, help="改造后库（含 v4 曲线）")
    ap.add_argument("--old-db", required=True, help="改造前库（v3 曲线）")
    ap.add_argument("--max-shift", type=float, default=MAX_SHIFT)
    ap.add_argument("--ref-method", default="quantile", choices=["quantile", "affine"],
                    help="语义参考点的构造方式：quantile=分位数映射(默认)，affine=经验仿射")
    ap.add_argument("--out", default="")
    ap.add_argument("--extra", default="", help="额外候选锚点表(JSON: [[v,a]×9])")
    args = ap.parse_args()

    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    meta = load_meta(args.new_db)
    new = load_curves(args.new_db, NEW_MODEL)
    old = load_curves(args.old_db)
    common = sorted(set(new) & set(old))
    print(f"新库 v4 曲线: {len(new)} 首；旧库: {len(old)} 首；配对: {len(common)} 首")

    # ── 经验映射 ──
    vo, vn, ao, an = [], [], [], []
    for sid in common:
        v_o, a_o = old[sid]
        v_n, a_n = new[sid]
        m = min(len(v_o), len(v_n))
        vo.append(v_o[:m])
        vn.append(v_n[:m])
        ao.append(a_o[:m])
        an.append(a_n[:m])
    vo = np.concatenate(vo)
    vn = np.concatenate(vn)
    ao = np.concatenate(ao)
    an = np.concatenate(an)

    alpha, beta, r2, rstd = fit_linear(vo, vn)
    print("\n[valence 映射] v_new = %.4f * v_old %+.4f   R²=%.4f  残差std=%.4f"
          % (alpha, beta, r2, rstd))
    print("  理论值 (B1: bias=0.25 scale=0.85): alpha=%.4f beta=%+.4f"
          % (1.0 / 0.85, -0.25 / 0.85))
    corr_a = float(np.corrcoef(ao, an)[0, 1])
    print("[arousal 映射] corr(a_old, a_new) = %.4f  (VA 头 → RMS 响度)" % corr_a)
    kx, ky = fit_monotone(ao, an)

    # ── 新旧分布的边缘统计（判断语义参考点该建在哪）──
    def stat(name, arr):
        print("  %-14s mean=%+.3f std=%.3f  p5=%+.3f p25=%+.3f p50=%+.3f p75=%+.3f p95=%+.3f"
              % (name, arr.mean(), arr.std(),
                 np.percentile(arr, 5), np.percentile(arr, 25), np.percentile(arr, 50),
                 np.percentile(arr, 75), np.percentile(arr, 95)))
    print("\n[边缘分布]")
    stat("valence 旧", vo)
    stat("valence 新", vn)
    stat("arousal 旧", ao)
    stat("arousal 新", an)

    # ── 语义参考点（原锚点搬到新坐标系）──
    ref_affine = []
    for gi in AUTO_IDX:
        v0, a0 = PRE_VA_ANCHORS[GROUPS[gi][3]]
        ref_affine.append((alpha * v0 + beta, float(np.interp(a0, kx, ky))))
    ref_quant = ref_by_quantile(vo, vn, ao, an)
    ref = ref_quant if args.ref_method == "quantile" else ref_affine
    print("\n[语义参考点] 原锚点 → 新坐标系（方法=%s）" % args.ref_method)
    for k, gi in enumerate(AUTO_IDX):
        g = GROUPS[gi]
        print("  %-6s (%+.2f,%+.2f) → 分位(%+.3f,%+.3f)  仿射(%+.3f,%+.3f)"
              % (g[3], g[1], g[2], ref_quant[k][0], ref_quant[k][1],
                 ref_affine[k][0], ref_affine[k][1]))

    songs = [{"id": sid, "v": new[sid][0], "a": new[sid][1]} for sid in common]

    # ── 候选方案 ──
    print("\n" + "=" * 92)
    print("  锚点候选（max_shift=%.2f 扫描）" % args.max_shift)
    print("=" * 92)
    hdr = ["方案", "词条极差", "冷门词条", "稳定率%", "半曲一致%", "ICC_V", "ICC_A",
           "负面组%", "最小间距"]
    rows = []

    cur_anchor = [(GROUPS[gi][1], GROUPS[gi][2]) for gi in AUTO_IDX]
    r = evaluate(songs, enforce_constraints(cur_anchor), meta)
    r["name"] = "现状锚点"
    r["anchors"] = enforce_constraints(cur_anchor)
    rows.append(r)
    print("  %-12s" % r["name"] + "".join("%12.3f" % r[h] for h in hdr[1:]))

    # 语义参考点直接作锚点（不向数据簇心挪动）—— 最忠实的"语义保持"方案
    anc = enforce_constraints([tuple(p) for p in ref])
    r = evaluate(songs, anc, meta)
    r["name"] = "语义参考点"
    r["anchors"] = anc
    rows.append(r)
    print("  %-12s" % r["name"] + "".join("%12.3f" % r[h] for h in hdr[1:]))

    # 手工语义网格
    anc = enforce_constraints([tuple(p) for p in HAND_GRID])
    r = evaluate(songs, anc, meta)
    r["name"] = "手工网格"
    r["anchors"] = anc
    rows.append(r)
    print("  %-12s" % r["name"] + "".join("%12.3f" % r[h] for h in hdr[1:]))

    # 外部候选（优化器产出），逗号分隔多个；文件含 pool 时逐个评估
    for path in [p for p in args.extra.split(",") if p.strip()]:
        with open(path.strip(), "r", encoding="utf-8") as f:
            extra = json.load(f)
        base = os.path.basename(path.strip()).replace(".json", "")
        if isinstance(extra, dict) and "pool" in extra:
            cands = [(c["anchors"], "%s#%d" % (base, i)) for i, c in enumerate(extra["pool"])]
            cands.append((extra["best"], base + "#best"))
        elif isinstance(extra, dict):
            cands = [(extra["best"], base)]
        else:
            cands = [(extra, base)]
        for raw, nm in cands:
            anc = enforce_constraints([tuple(p) for p in raw])
            r = evaluate(songs, anc, meta)
            r["name"] = nm
            r["anchors"] = anc
            rows.append(r)
            print("  %-12s" % r["name"][:12] + "".join("%12.3f" % r[h] for h in hdr[1:]))

    for ms in (0.25, 0.40, 0.60, 0.80, 1.20, 99.0):
        anc = place_anchors(songs, ref, max_shift=ms)
        anc = enforce_constraints(anc)
        r = evaluate(songs, anc, meta)
        r["name"] = ("无约束" if ms >= 99 else "max_shift=%.2f" % ms)
        r["anchors"] = anc
        rows.append(r)
        print("  %-12s" % r["name"] + "".join("%12.3f" % r[h] for h in hdr[1:]))

    # 各变体的锚点表全量展开
    print("\n" + "=" * 92)
    print("  各变体逐窗落点占比 %（语义判读用）")
    print("=" * 92)
    words = [GROUPS[gi][3] for gi in AUTO_IDX]
    names = [r["name"] for r in rows]
    print("  %-8s" % "方案" + "".join("%9s" % w for w in words))
    for r in rows:
        print("  %-8s" % r["name"] + "".join("%9.1f" % r["share"][w] for w in words))

    print("\n" + "=" * 92)
    print("  各变体锚点表")
    print("=" * 92)
    print("  %-8s" % "组" + "".join("%22s" % n for n in names))
    for k, gi in enumerate(AUTO_IDX):
        g = GROUPS[gi]
        line = "  %-8s" % g[3]
        for r in rows:
            line += "%22s" % ("(%+.3f,%+.3f)" % (r["anchors"][k][0], r["anchors"][k][1]))
        print(line)

    best = rows[-1]
    print("\n" + "=" * 92)
    print("  锚点表对比")
    print("=" * 92)
    print("  %-8s %-18s %-18s %-18s" % ("组", "现状锚点", "语义参考点", "重标定锚点(0.40)"))
    pick = next(r for r in rows if r["name"] == "max_shift=0.40")
    for k, gi in enumerate(AUTO_IDX):
        g = GROUPS[gi]
        print("  %-8s (%+.2f,%+.2f)      (%+.3f,%+.3f)      (%+.3f,%+.3f)"
              % (g[3], g[1], g[2], ref[k][0], ref[k][1],
                 pick["anchors"][k][0], pick["anchors"][k][1]))

    for pick_name in ("语义参考点", "max_shift=0.40", "无约束", "现状锚点"):
        p = next((r for r in rows if r["name"] == pick_name), None)
        if p is None:
            continue
        print("\n  top1 词条分布（%s）:" % pick_name)
        for w, c in p["top1"].most_common():
            print("    %-6s %4d 首 (%5.1f%%)" % (w, c, c / p["n"] * 100))

    # ── 验收矩阵：产品判据 + 软指标 ───────────────────────────────────
    print("\n" + "=" * 100)
    print("  验收矩阵（H=硬判据，S=软指标）")
    print("=" * 100)
    hard = [
        ("H1 燃=最高arousal", lambda a, s: a[1][1] >= max(p[1] for p in a) - 1e-6),
        ("H2 静谧=最低arousal", lambda a, s: a[4][1] <= min(p[1] for p in a) + 1e-6),
        ("H3 窒息=最低valence", lambda a, s: a[8][0] <= min(p[0] for p in a) + 1e-6),
        ("H4 元气/治愈=valence前二",
         lambda a, s: min(a[0][0], a[3][0])
         >= max(a[k][0] for k in range(9) if k not in (0, 3)) - 0.10
         and abs(a[0][0] - a[3][0]) <= 0.25),
        ("H5 负面组=emo/躁/窒息",
         lambda a, s: {GROUPS[AUTO_IDX[k]][3] for k in range(9) if a[k][0] < 0}
         == {"emo", "躁", "窒息"}),
        ("H6 燃arousal≥0.6", lambda a, s: a[1][1] >= 0.6),
        ("H7 神圣窗占比≤18%", lambda a, s: s["神圣"] <= 18.0),
        ("H8 燃窗占比≥8%", lambda a, s: s["燃"] >= 8.0),
        ("H9 每组窗占比≥3%", lambda a, s: min(s.values()) >= 3.0),
        ("H10 最小间距≥0.25", lambda a, s: min_spacing_of(a) >= 0.25),
        ("H11 |锚点|≤0.95", lambda a, s: max(abs(p[0]) for p in a) <= 0.95
         and max(abs(p[1]) for p in a) <= 0.95),
    ]
    print("  %-16s" % "方案" + "".join("%4s" % ("H%d" % (i + 1)) for i in range(len(hard)))
          + "   %8s %8s %8s %8s" % ("稳定率", "半曲", "极差", "冷门"))
    for r in rows:
        marks = []
        for _, fn in hard:
            try:
                ok = fn(r["anchors"], r["share"])
            except Exception:
                ok = False
            marks.append(" ✓ " if ok else " ✗ ")
        print("  %-16s" % r["name"][:16] + "".join("%4s" % m for m in marks)
              + "   %8.2f %8.2f %8.2f %8.0f"
              % (r["稳定率%"], r["半曲一致%"], r["词条极差"], r["冷门词条"]))
    print("\n  H 判据含义:")
    for i, (name, _) in enumerate(hard):
        print("    H%d %s" % (i + 1, name))

    if args.out:
        with open(args.out, "w", encoding="utf-8", newline="\r\n") as f:
            f.write("# 锚点重标定报告（全库真值）\n\n")
            f.write("新库: `%s`\n\n旧库: `%s`\n\n配对样本: %d 首\n\n"
                    % (args.new_db, args.old_db, len(common)))
            f.write("## 经验映射\n\n")
            f.write("- valence: `v_new = %.4f * v_old %+.4f`（R²=%.4f, 残差std=%.4f）\n"
                    % (alpha, beta, r2, rstd))
            f.write("- arousal: corr(a_old, a_new) = %.4f\n\n" % corr_a)
            f.write("## 方案对比\n\n| " + " | ".join(hdr) + " |\n")
            f.write("|" + "---|" * len(hdr) + "\n")
            for r in rows:
                f.write("| " + " | ".join([r["name"]] + [f"{r[h]:.3f}" for h in hdr[1:]]) + " |\n")
            f.write("\n## 锚点表\n\n| 组 | 现状 | 语义参考点 | 重标定(0.40) |\n|---|---|---|---|\n")
            for k, gi in enumerate(AUTO_IDX):
                g = GROUPS[gi]
                f.write("| %s | (%+.2f, %+.2f) | (%+.3f, %+.3f) | (%+.3f, %+.3f) |\n"
                        % (g[3], g[1], g[2], ref[k][0], ref[k][1],
                           pick["anchors"][k][0], pick["anchors"][k][1]))
        print(f"\n  已写出: {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
