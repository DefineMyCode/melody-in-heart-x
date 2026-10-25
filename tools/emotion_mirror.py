#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
情绪词表/锚点表的**单一真源镜像**。

以前这里手抄过一份 Kotlin 的锚点表(tools/emotion_diag.py 的 GROUPS / LEGACY_GROUPS),
漂移是**真实发生过的故障**: 9bd99f1 之后我把 auto 数组下标与组声明序搞混,
导致并列兜底序错位 —— 靠单测与镜像互校才发现。

现在改成**直接解析源文件**, 不再手抄:
  · core/model/src/main/java/cn/com/dcsgo/mihx/core/model/EmotionGroup.kt
      → 枚举常量(词 + 新锚点 + auto 标记) 与 LEGACY_ANCHORS(旧锚点)
  · core/model/emotion_model_spec.properties
      → 坐标系标签 + A/B 参数 + 资产哈希 → 算出与 Gradle 同一套指纹

指纹算法必须与 core/model/build.gradle.kts 的 generateEmotionModelSpec **逐字一致**:
    paramsMaterial = label + "\\n" + dbFloor + "\\n" + dbCeil + "\\n" + vCalBias + "\\n" + vCalScale
    fullMaterial   = paramsMaterial + "\\n" + yamnetSha256 + "\\n" + headSha256
    paramsFingerprint = sha256(paramsMaterial)[:8]
    fingerprint        = sha256(fullMaterial)[:8]
    modelVersion       = label + "@" + fingerprint
若 :core:model 的生成物存在, 本模块会与它**交叉校验**, 对不上直接抛错(响亮失败)。
"""
import hashlib
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KOTLIN_SRC = os.path.join(
    ROOT, "core", "model", "src", "main", "java", "cn", "com", "dcsgo", "mihx",
    "core", "model", "EmotionGroup.kt",
)
SPEC_FILE = os.path.join(ROOT, "core", "model", "emotion_model_spec.properties")
GENERATED_KOTLIN = os.path.join(
    ROOT, "core", "model", "build", "generated", "emotionModelSpec", "kotlin",
    "cn", "com", "dcsgo", "mihx", "core", "model", "EmotionModelSpec.kt",
)

FINGERPRINT_SEPARATOR = "@"


def _read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def _sha256(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# ── 规格清单 ────────────────────────────────────────────────────────────
def _parse_spec():
    props = {}
    for line in _read(SPEC_FILE).splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        props[key.strip()] = value.strip()
    required = ["label", "dbFloor", "dbCeil", "vCalBias", "vCalScale",
                "yamnetSha256", "headSha256"]
    missing = [k for k in required if not props.get(k)]
    if missing:
        raise RuntimeError(
            "core/model/emotion_model_spec.properties 缺少必填项: %s" % ", ".join(missing)
        )
    return props


SPEC = _parse_spec()

CURRENT_LABEL = SPEC["label"]

# 指纹算法 —— 必须与 Gradle 侧逐字一致(见本文件顶部说明)
_PARAMS_MATERIAL = "\n".join([
    SPEC["label"], SPEC["dbFloor"], SPEC["dbCeil"],
    SPEC["vCalBias"], SPEC["vCalScale"],
])
_FULL_MATERIAL = "\n".join([
    _PARAMS_MATERIAL, SPEC["yamnetSha256"], SPEC["headSha256"],
])
PARAMS_FINGERPRINT = _sha256(_PARAMS_MATERIAL)[:8]
FINGERPRINT = _sha256(_FULL_MATERIAL)[:8]
CURRENT_MODEL_VERSION = CURRENT_LABEL + FINGERPRINT_SEPARATOR + FINGERPRINT


# ── Kotlin 源解析 ───────────────────────────────────────────────────────
_ENTRY_RE = re.compile(
    r"^\s+([A-Z][A-Z0-9_]*)\(listOf\((?P<words>[^)]*)\),\s*"
    r"(?P<v>-?[0-9.]+)f,\s*(?P<a>-?[0-9.]+)f(?P<auto>, auto = (?:true|false))?\),"
)
_LEGACY_RE = re.compile(
    r"^\s+([A-Z][A-Z0-9_]*) to \((?P<v>-?[0-9.]+)f to (?P<a>-?[0-9.]+)f\),"
)
_WORD_RE = re.compile(r'"([^"]+)"')
_LEGACY_LABEL_RE = re.compile(r'LEGACY_MODEL_LABEL\s*=\s*"([^"]+)"')


def _parse_kotlin():
    src = _read(KOTLIN_SRC)

    groups = []
    for line in src.splitlines():
        m = _ENTRY_RE.match(line)
        if not m:
            continue
        words = _WORD_RE.findall(m.group("words"))
        if not words:
            continue
        # Kotlin 侧默认 auto = true, 只有显式写 `auto = false` 的组才退出投票
        auto_raw = m.group("auto")
        groups.append({
            "name": m.group(1),
            "v": float(m.group("v")),
            "a": float(m.group("a")),
            "headline": words[0],
            "words": words,
            "auto": not (auto_raw is not None and "false" in auto_raw),
        })
    if not groups:
        raise RuntimeError("从 %s 里没解析出任何情绪组, 镜像已失效" % KOTLIN_SRC)

    legacy = {}
    in_block = False
    for line in src.splitlines():
        if "LEGACY_ANCHORS" in line and "Map<" in line:
            in_block = True
            continue
        if in_block:
            m = _LEGACY_RE.match(line)
            if m:
                legacy[m.group(1)] = (float(m.group("v")), float(m.group("a")))
                continue
            if line.strip() == ")":
                in_block = False
    if not legacy:
        raise RuntimeError("从 %s 里没解析出 LEGACY_ANCHORS, 镜像已失效" % KOTLIN_SRC)

    m = _LEGACY_LABEL_RE.search(src)
    if not m:
        raise RuntimeError("从 %s 里没解析出 LEGACY_MODEL_LABEL" % KOTLIN_SRC)
    legacy_label = m.group(1)

    legacy_groups = []
    for g in groups:
        if g["name"] not in legacy:
            raise RuntimeError("旧锚点表缺少组 %s" % g["name"])
        v, a = legacy[g["name"]]
        legacy_groups.append({
            "name": g["name"], "v": v, "a": a,
            "headline": g["headline"], "words": g["words"], "auto": g["auto"],
        })
    return groups, legacy_groups, legacy_label


_GROUPS_D, _LEGACY_GROUPS_D, LEGACY_LABEL = _parse_kotlin()

# 与 Kotlin 侧结构一致: (name, anchorV, anchorA, headline, auto)
GROUPS = [(g["name"], g["v"], g["a"], g["headline"], g["auto"]) for g in _GROUPS_D]
LEGACY_GROUPS = [
    (g["name"], g["v"], g["a"], g["headline"], g["auto"]) for g in _LEGACY_GROUPS_D
]
GROUPS_BY_NAME = {g["name"]: g for g in _GROUPS_D}
WORD_TO_GROUP = {}
for _g in _GROUPS_D:
    for _w in _g["words"]:
        WORD_TO_GROUP[_w] = _g["name"]


def groups_for(model_version):
    """曲线所属坐标系 → 锚点表(组序与 GROUPS 一致)。

    与 Kotlin 的 EmotionGroup.anchorsFor 同规则: 按版本串里**标签**部分
    ('@' 之前)选表, 指纹不参与选表。
    """
    label = label_of(model_version)
    if label == CURRENT_LABEL:
        return GROUPS
    if label == LEGACY_LABEL:
        return LEGACY_GROUPS
    # 未登记的标签按旧坐标系处理(与 Kotlin 一致)
    return LEGACY_GROUPS


def label_of(model_version):
    """版本串 → 坐标系标签('@' 之前)。"""
    return model_version.split(FINGERPRINT_SEPARATOR)[0]


def label_pattern(model_version):
    """SQL 用的标签前缀匹配串 —— 指纹变了也能查到同一坐标系的行。

    工具按精确 modelVersion 过滤的话, 每次换指纹就得改工具; 按标签前缀匹配
    才是"坐标系"该有的粒度。
    """
    return label_of(model_version) + "%"


def _cross_check_generated():
    """与 :core:model 的生成物交叉校验 —— 对不上说明指纹算法漂移了, 响亮失败。"""
    if not os.path.isfile(GENERATED_KOTLIN):
        return
    src = _read(GENERATED_KOTLIN)
    for name, expected in (
        ("MODEL_VERSION", CURRENT_MODEL_VERSION),
        ("PARAMS_FINGERPRINT", PARAMS_FINGERPRINT),
        ("FINGERPRINT", FINGERPRINT),
        ("LABEL", CURRENT_LABEL),
    ):
        m = re.search(r'const val %s = "([^"]*)"' % name, src)
        if m and m.group(1) != expected:
            raise RuntimeError(
                "指纹算法与 :core:model 的生成物不一致: %s 生成=%s 本模块=%s\n"
                "  (tools/emotion_mirror.py 与 core/model/build.gradle.kts 的算法必须一致)"
                % (name, m.group(1), expected)
            )


_cross_check_generated()

if __name__ == "__main__":
    print("current label   :", CURRENT_LABEL)
    print("current version :", CURRENT_MODEL_VERSION)
    print("params fingerprint:", PARAMS_FINGERPRINT)
    print("legacy label    :", LEGACY_LABEL)
    print("groups          :", len(GROUPS))
    for g in GROUPS:
        print("   %-8s (%+.3f, %+.3f)  %-4s auto=%s" % (g[0], g[1], g[2], g[3], g[4]))
