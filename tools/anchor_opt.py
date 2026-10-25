#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
锚点表的**受约束优化**（全库真值）。

为什么不用手拍 / 纯机械变换:
  - 纯分位数映射(语义参考点)保住语义但「燃」只剩 4.9% 窗 —— 违背方案 A 的初衷
  - Lloyd 数据驱动会让「燃」的 arousal 从 +0.755 掉到 +0.490，稳定性反而下降
  所以: 在"语义蓝图"约束下, 用数据把锚点表搜出来。

约束（硬）:
  - 每组逐窗占比 ∈ [3%, 22%]；「燃」≥ 6%；「神圣」≤ 18%
  - 组间最小间距 ≥ 0.25；|anchor| ≤ 0.95
  - 语义序不许反转（valence/arousal 的相对次序按原锚点表，带容差）
目标: 最大化「窗口归属优势」(top1 占比 - 次高占比) —— 它是 Bootstrap 稳定性
      与半曲一致性的快代理；搜完再用真指标复核。

用法:
    python tools/anchor_opt.py --db <新库> [--iters 4000] [--seed 7] [--out cand.json]
"""
import argparse
import io
import json
import math
import os
import sqlite3
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emotion_diag import AUTO_IDX, GROUPS, LOW_CONF, PRE_VA_ANCHORS  # noqa: E402
from emotion_mirror import CURRENT_MODEL_VERSION, label_pattern  # noqa: E402

NEW_MODEL = CURRENT_MODEL_VERSION
MIN_SPACING = 0.25
BOUND = 0.95
SHARE_MIN = 3.0
SHARE_MAX = 22.0
RAGE_MIN = 8.0
EPIC_MAX = 18.0
ORDER_TOL = 0.12   # 语义序允许的反转容差
TIE_TOL = 0.25     # 原设计中并列(差 ≤0.05)的组，新坐标下也不得分离超过此值
TIE_GAP = 0.08     # 严格模式：并列组新坐标下必须几乎重合

HEAD = [GROUPS[gi][3] for gi in AUTO_IDX]
# 语义序参照物 = **改造前**的原始锚点（冻结引用），不是当前 EmotionGroup 的值。
OLD_V = np.array([PRE_VA_ANCHORS[w][0] for w in HEAD])
OLD_A = np.array([PRE_VA_ANCHORS[w][1] for w in HEAD])
RAGE = HEAD.index("燃")
EPIC = HEAD.index("神圣")
STILL = HEAD.index("静谧")
ABYSS = HEAD.index("窒息")
BRIGHT = HEAD.index("元气")
LOVE = HEAD.index("治愈")
SAD = HEAD.index("emo")
ANXIOUS = HEAD.index("躁")
NEG_SET = {SAD, ANXIOUS, ABYSS}

# 产品硬判据（做进优化器，出来的表天然满足）
MARGIN = 0.02       # 极值组与次极值组的最小差距
TOP2_TOL = 0.10     # 元气/治愈 相对其他组的 valence 领先幅度
TIE_MAX = 0.20      # 元气/治愈 两者 valence 的最大分离


def semantic_hard_ok(anchors):
    """产品硬判据：语义极值必须成立。返回 True/False。"""
    v = np.array([p[0] for p in anchors])
    a = np.array([p[1] for p in anchors])
    others_a = np.delete(a, RAGE)
    others_v = np.delete(v, ABYSS)
    if a[RAGE] < others_a.max() - MARGIN:
        return False          # H1 燃必须独占最高 arousal
    if a[STILL] > np.delete(a, STILL).min() + MARGIN:
        return False          # H2 静谧必须独占最低 arousal
    if v[ABYSS] > others_v.min() + MARGIN:
        return False          # H3 窒息必须独占最低 valence
    if a[RAGE] < 0.60:
        return False          # H6 燃必须够得着高能量区
    pair = sorted([v[BRIGHT], v[LOVE]])
    rest = sorted(v[i] for i in range(len(v)) if i not in (BRIGHT, LOVE))
    if pair[0] < rest[-1] - TOP2_TOL:
        return False          # H4 元气/治愈 必须是 valence 前二
    if abs(pair[1] - pair[0]) > TIE_MAX:
        return False          # H4 二者不得分离过大
    neg = {i for i in range(len(v)) if v[i] < 0}
    if neg != NEG_SET:
        return False          # H5 负面组必须恰好是 emo/躁/窒息
    return True


def load_windows(db, model_version=NEW_MODEL):
    """→ (win_v, win_a, song_idx, n_songs, song_ids)"""
    con = sqlite3.connect(db)
    try:
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    except Exception:
        pass
    rows = con.execute(
        "SELECT songId, curveJson FROM song_emotions "
        "WHERE windowsAnalyzed > 0 AND modelVersion LIKE ?", (label_pattern(model_version),)
    ).fetchall()
    con.close()
    vs, as_, idx, ids = [], [], [], []
    for i, (sid, cj) in enumerate(rows):
        arr = json.loads(cj)
        n = len(arr) // 2
        if n < 2:
            continue
        v = np.array([arr[k * 2] for k in range(n)])
        a = np.array([arr[k * 2 + 1] for k in range(n)])
        ids.append(sid)
        vs.append(v)
        as_.append(a)
        idx.append(np.full(n, i, dtype=np.int32))
    return (np.concatenate(vs), np.concatenate(as_), np.concatenate(idx), len(ids), ids)


def assign(wv, wa, av, aa):
    """逐窗最近锚点 → cell id；近零窗弃权(-1)。"""
    keep = ~((np.abs(wv) < LOW_CONF) & (np.abs(wa) < LOW_CONF))
    cell = np.full(len(wv), -1, dtype=np.int32)
    if keep.any():
        d = ((av[None, :] - wv[keep, None]) ** 2 + (aa[None, :] - wa[keep, None]) ** 2)
        cell[keep] = d.argmin(axis=1)
    return cell, keep


BOXES = {
    # 语义蓝图在**新坐标系**下的取值范围（v 区间, a 区间）
    # valence 必须沿用原设计的正负分组：正面 {元气,燃,神圣,治愈,静谧,放松} / 负面 {emo,躁,窒息}
    "元气": ((0.42, 0.72), (0.05, 0.42)),    # 高正面
    "燃": ((0.00, 0.32), (0.62, 0.85)),     # 能量最高
    "神圣": ((0.00, 0.32), (0.20, 0.50)),    # 中性偏宏大
    "治愈": ((0.42, 0.72), (-0.32, 0.10)),   # 高正面 + 低能量
    "静谧": ((0.00, 0.24), (-0.95, -0.60)),  # 能量最低
    "emo": ((-0.88, -0.50), (-0.62, -0.22)), # 负面 + 低能量
    "躁": ((-0.88, -0.48), (0.42, 0.70)),    # 负面 + 高能量
    "放松": ((0.02, 0.34), (-0.55, -0.08)),  # 中性 + 低能量
    "窒息": ((-0.95, -0.70), (-0.30, 0.08)), # 最负面
}


def random_feasible(rng):
    """在语义蓝图盒子里随机采样, 返回满足硬判据的锚点表（多轮尝试）。"""
    for _ in range(400):
        cand = []
        for w in HEAD:
            (vlo, vhi), (alo, ahi) = BOXES[w]
            cand.append((float(rng.uniform(vlo, vhi)), float(rng.uniform(alo, ahi))))
        if semantic_hard_ok(cand):
            # 间距与占比再过一遍
            ok = True
            for i in range(len(cand)):
                for j in range(i + 1, len(cand)):
                    if math.hypot(cand[i][0] - cand[j][0], cand[i][1] - cand[j][1]) < MIN_SPACING:
                        ok = False
            if ok:
                return cand
    return None


def score(anchors, wv, wa, sidx, nsongs, order_tol=ORDER_TOL, tie_tol=TIE_TOL,
          tie_gap=TIE_GAP):
    """返回 (目标值, 诊断字典)。目标 = top1 优势（Bootstrap 稳定性的快代理）。"""
    av = np.array([p[0] for p in anchors])
    aa = np.array([p[1] for p in anchors])
    cell, keep = assign(wv, wa, av, aa)
    # 硬约束：边界 + 最小间距 + 语义极值
    if np.abs(anchors).max() > BOUND:
        return -1e9, None
    for i in range(len(anchors)):
        for j in range(i + 1, len(anchors)):
            d = math.hypot(anchors[i][0] - anchors[j][0], anchors[i][1] - anchors[j][1])
            if d < MIN_SPACING:
                return -1e9, None
    if not semantic_hard_ok(anchors):
        return -1e9, None
    # 逐窗占比
    wshare = np.bincount(cell[keep], minlength=len(anchors)) / max(keep.sum(), 1) * 100
    if wshare.min() < SHARE_MIN or wshare.max() > SHARE_MAX:
        return -1e9, None
    if wshare[RAGE] < RAGE_MIN or wshare[EPIC] > EPIC_MAX:
        return -1e9, None
    # 语义序（带容差）
    pen = 0.0
    for i in range(len(anchors)):
        for j in range(len(anchors)):
            if i == j:
                continue
            if OLD_V[i] > OLD_V[j] + 0.05 and anchors[i][0] < anchors[j][0] - order_tol:
                pen += (anchors[j][0] - anchors[i][0]) ** 2
            if OLD_A[i] > OLD_A[j] + 0.05 and anchors[i][1] < anchors[j][1] - order_tol:
                pen += (anchors[j][1] - anchors[i][1]) ** 2
            # 原设计里并列的组（元气/治愈 valence 都是 +0.60），新坐标下不得分离
            if abs(OLD_V[i] - OLD_V[j]) <= 0.05:
                d = abs(anchors[i][0] - anchors[j][0])
                lim = min(tie_tol, tie_gap) if tie_gap > 0 else tie_tol
                if d > lim:
                    pen += (d - lim) ** 2
    # 每首歌的 top1 优势
    cs = cell[keep]
    ws = sidx[keep]
    flat = ws * len(anchors) + cs
    cnt = np.bincount(flat, minlength=nsongs * len(anchors)).reshape(nsongs, len(anchors))
    tot = cnt.sum(axis=1, keepdims=True)
    with np.errstate(invalid="ignore", divide="ignore"):
        ratio = np.where(tot > 0, cnt / np.maximum(tot, 1), 0.0)
    srt = np.sort(ratio, axis=1)
    p1 = srt[:, -1]
    p2 = srt[:, -2]
    margin = np.clip((p1 - p2) / 0.15, 0.0, 1.0)
    obj = float(margin.mean()) - 0.05 * pen
    return obj, {
        "share": wshare.tolist(),
        "p1": float(p1.mean()),
        "p2": float(p2.mean()),
        "margin": float(margin.mean()),
        "pen": pen,
        "empty_songs": int((tot[:, 0] == 0).sum()),
    }


def refine(anchors, wv, wa, sidx, nsongs, rng, rounds=6,
           order_tol=ORDER_TOL, tie_tol=TIE_TOL, tie_gap=TIE_GAP):
    """坐标下降：逐组小步移动, 步长逐轮收缩。"""
    cur = [tuple(p) for p in anchors]
    best, _ = score(cur, wv, wa, sidx, nsongs, order_tol, tie_tol, tie_gap)
    step = 0.18
    for _ in range(rounds):
        improved = False
        for g in range(len(cur)):
            for _try in range(24):
                cand = list(cur)
                cand[g] = (cur[g][0] + rng.uniform(-step, step),
                           cur[g][1] + rng.uniform(-step, step))
                s, _ = score(cand, wv, wa, sidx, nsongs, order_tol, tie_tol, tie_gap)
                if s > best + 1e-9:
                    cur = cand
                    best = s
                    improved = True
                    break
        step *= 0.55
        if not improved and step < 0.02:
            break
    return cur, best


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True)
    ap.add_argument("--iters", type=int, default=250)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--out", default="")
    ap.add_argument("--start", default="", help="起始锚点表 JSON")
    ap.add_argument("--order-tol", type=float, default=ORDER_TOL,
                    help="语义序反转容差；0 = 完全不许反转")
    ap.add_argument("--tie-gap", type=float, default=TIE_GAP,
                    help="并列组在新坐标下的最大允许分离；0 = 用 tie_tol")
    args = ap.parse_args()

    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    wv, wa, sidx, nsongs, ids = load_windows(args.db)
    print(f"窗口 {len(wv)} 个 / {nsongs} 首（modelVersion={NEW_MODEL}）")

    rng = np.random.default_rng(args.seed)
    # 起点：--start 指定的表(优先) + 手工网格 + 随机可行点
    starts = []
    if args.start:
        with io.open(args.start, encoding="utf-8") as f:
            st0 = json.load(f)
        st0 = [(float(a[0]), float(a[1])) for a in st0]
        s_start, _ = score(st0, wv, wa, sidx, nsongs)
        if s_start > -1e8:
            starts.append(st0)
            print("起点表(--start)已纳入, obj=%.4f" % s_start)
        else:
            print("起点表(--start)不合规(违反硬约束), 忽略")
    hand = [(0.52, 0.22), (0.15, 0.72), (0.05, 0.40), (0.48, -0.12),
            (-0.12, -0.78), (-0.58, -0.42), (-0.60, 0.55), (0.15, -0.42),
            (-0.85, -0.02)]
    s0, _ = score(hand, wv, wa, sidx, nsongs)
    if s0 > -1e8:
        starts.append(hand)
    n_feas = 0
    for _ in range(args.iters // 4):
        c = random_feasible(rng)
        if c is None:
            continue
        s, _ = score(c, wv, wa, sidx, nsongs)
        if s <= -1e8:
            continue
        n_feas += 1
        starts.append(c)
    print(f"可行起点: {len(starts)}（手工网格{'已纳入' if s0 > -1e8 else '不合规'}）")

    results = []
    pool = []
    for si, st in enumerate(starts):
        cur, best = refine(st, wv, wa, sidx, nsongs, rng,
                           order_tol=args.order_tol, tie_gap=args.tie_gap)
        pool.append((best, cur))
        if si % 20 == 0:
            print(f"  起点{si}/{len(starts)}: obj={best:.4f}")

    pool.sort(key=lambda x: -x[0])
    print(f"起点精修完成，最优 obj={pool[0][0]:.4f}")

    # 去重后取前 N
    pool.sort(key=lambda x: -x[0])
    picked = []
    for obj, anc in pool:
        key = tuple(round(v, 3) for p in anc for v in p)
        if any(key == tuple(round(v, 3) for p in q for v in p) for _, q in picked):
            continue
        picked.append((obj, anc))
        if len(picked) >= 8:
            break

    best_obj, best_anc = picked[0]
    s, diag = score(best_anc, wv, wa, sidx, nsongs, args.order_tol, TIE_TOL, args.tie_gap)
    print("\n" + "=" * 84)
    print("  最优锚点表 (obj=%.4f)" % best_obj)
    print("=" * 84)
    for k, gi in enumerate(AUTO_IDX):
        g = GROUPS[gi]
        print("  %-6s 旧(%+.2f,%+.2f) → 新(%+.3f,%+.3f)   窗占比 %5.1f%%"
              % (g[3], PRE_VA_ANCHORS[g[3]][0], PRE_VA_ANCHORS[g[3]][1],
                 best_anc[k][0], best_anc[k][1], diag["share"][k]))
    print("  top1 均值 %.3f / 次高均值 %.3f / 优势 %.3f / 空窗歌 %d"
          % (diag["p1"], diag["p2"], diag["margin"], diag["empty_songs"]))
    # 语义序检查
    inv = []
    for i in range(len(best_anc)):
        for j in range(len(best_anc)):
            if i == j:
                continue
            if OLD_V[i] > OLD_V[j] + 0.05 and best_anc[i][0] < best_anc[j][0] - 1e-6:
                inv.append("%s<%s(V)" % (HEAD[i], HEAD[j]))
            if OLD_A[i] > OLD_A[j] + 0.05 and best_anc[i][1] < best_anc[j][1] - 1e-6:
                inv.append("%s<%s(A)" % (HEAD[i], HEAD[j]))
    print("  语义序反转: %s" % (", ".join(inv) if inv else "无"))

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump({"best": [[float(a), float(b)] for a, b in best_anc],
                       "pool": [{"obj": o,
                                 "anchors": [[float(a), float(b)] for a, b in anc]}
                                for o, anc in picked]}, f, indent=1)
        print(f"\n  已写出: {args.out}（含 {len(picked)} 个候选）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
