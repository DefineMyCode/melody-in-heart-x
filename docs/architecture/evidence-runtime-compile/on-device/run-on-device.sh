#!/usr/bin/env bash
# 真机复验复跑脚本（2026-09-29）
#
# 对应 RUNTIME_COMPILE_FEASIBILITY.md §九。
# 用途：在真安卓运行时上验证「Kotlin 编译器能否跑起来」，
#       以及「类级探测为何不足以判断兼容性」。
#
# 前置：
#   - /opt/android-sdk 下含 platform-tools/adb、build-tools/36.0.0/d8、
#     platforms/android-36/android.jar
#   - 已启动模拟器或连真机（adb devices 可见）
#   - kotlin-compiler-embeddable jar（本脚本用 /tmp/feas/jars/ 里那份 2.0.21）
#
# 用法：bash run-on-device.sh
set -uo pipefail

SDK=/opt/android-sdk
ADB=$SDK/platform-tools/adb
D8=$SDK/build-tools/36.0.0/d8
AJAR=$SDK/platforms/android-36/android.jar
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "===== 0) 环境 ====="
$ADB devices
$ADB shell getprop ro.build.version.sdk
$ADB shell getprop ro.build.version.release

echo
echo "===== 1) 编译并推送探针 ====="
TMP=$(mktemp -d)
for n in Probe Probe2 Probe3; do
  javac -nowarn -d "$TMP" "$HERE/$n.java" || exit 1
  mkdir -p "$TMP/out-$n"
  "$D8" --output "$TMP/out-$n/" --lib "$AJAR" "$TMP/$n.class" || exit 1
  $ADB push "$TMP/out-$n/classes.dex" "/data/local/tmp/$n.jar" >/dev/null
done

echo
echo "===== 2) 运行时 JDK 面探针（类级）====="
$ADB shell "CLASSPATH=/data/local/tmp/Probe.jar app_process /system/bin Probe"

echo
echo "===== 3) Unsafe 方法级探针（★ 说明类级探测会骗人）====="
$ADB shell "CLASSPATH=/data/local/tmp/Probe2.jar app_process /system/bin Probe2"

echo
echo "===== 4) Unsafe.copyMemory 签名精确对照 ====="
$ADB shell "CLASSPATH=/data/local/tmp/Probe3.jar app_process /system/bin Probe3"

echo
echo "===== 5) ★ 决定性实验：用 ART 拉起真实 Kotlin 编译器 ====="
echo "--- 前置：把 kotlin-compiler-embeddable 转 dex 并打包 ---"
if [ -d /tmp/feas/dexfull ]; then
  python3 - <<'PY'
import zipfile, glob, os
out = "/tmp/feas/kc2.jar"
if not os.path.exists(out):
    src = "/tmp/feas/jars/kotlin-compiler-embeddable-2.0.21.jar"
    with zipfile.ZipFile(src) as z, zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as o:
        for f in sorted(glob.glob("/tmp/feas/dexfull/*.dex")):
            o.write(f, os.path.basename(f))
        # PathUtil 需要这个 .class 资源，否则报 "Resource not found"
        n = "org/jetbrains/kotlin/utils/PathUtil.class"
        if n in z.namelist():
            o.writestr(n, z.read(n))
    print("已构建", out)
else:
    print("已存在", out)
PY
  $ADB push /tmp/feas/kc2.jar /data/local/tmp/kc2.jar >/dev/null
  $ADB shell "mkdir -p /data/local/tmp/src && printf 'fun main() { println(\"hi\")\ }\n' > /data/local/tmp/src/Hello.kt"
  echo "--- 5a) 裸跑（预期：撞 JDK 检测 / Unsafe 缺方法）---"
  $ADB shell "CLASSPATH=/data/local/tmp/kc2.jar app_process /system/bin \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -d /data/local/tmp/out /data/local/tmp/src/Hello.kt" 2>&1 | head -20
  echo "--- 5b) -no-jdk -no-stdlib 绕过 JDK 检测（预期：撞 Unsafe.copyMemory 缺 5 参重载）---"
  $ADB shell "CLASSPATH=/data/local/tmp/kc2.jar app_process /system/bin \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-jdk -no-stdlib \
    -d /data/local/tmp/out /data/local/tmp/src/Hello.kt" 2>&1 | head -12
else
  echo "跳过：/tmp/feas/dexfull 不存在（需先跑初版桌面实验生成编译器 dex）"
fi

echo
echo "===== 完成 ====="
echo "预期结论：编译器初始化失败（java.lang.Error: NoSuchMethodException: copyMemory），"
echo "          且 Unsafe 的类存在、方法名存在、但缺 5 参签名 —— 类级探测查不出。"
