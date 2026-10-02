"""Measure the resource cost of one on-device compile (time + peak heap).
Run a full-JDK compile with -verbose:gc to observe heap pressure.
"""
import glob, os, shutil, subprocess, time

TOOLCP = ":".join(sorted(glob.glob("/tmp/feas/jars/*.jar")))
COMPOSE_RT = "/tmp/feas/jars/cra/classes.jar"
PLUGIN = "/tmp/feas/jars/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar"
SRC = "/tmp/feas/run4/src"
OUT = "/tmp/feas/timing_out"

shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT, exist_ok=True)

args = ["java", "-Xmx512m", "-cp", TOOLCP, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-Xplugin=" + PLUGIN,
        "-P", "plugin:androidx.compose.compiler.plugins.kotlin:liveLiteralsEnabled=false",
        "-no-stdlib", "-classpath", COMPOSE_RT + ":/tmp/feas/jars/kotlin-stdlib-2.0.21.jar",
        "-d", OUT, SRC]

for label, flags in [("cold (default JVM, 512MB heap)", ["-Xmx512m"]),
                     ("cold 2GB heap", ["-Xmx2g"])]:
    shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT, exist_ok=True)
    a = ["java"] + flags + args[1:]
    t0 = time.time()
    r = subprocess.run(a, capture_output=True, text=True, timeout=900)
    dt = time.time() - t0
    ok = bool(glob.glob(OUT + "/p/*.class"))
    print(f"{label}: {dt:.2f}s  exit={r.returncode}  ok={ok}")

# heap pressure: how much does it need?
for heap in ["-Xmx256m", "-Xmx384m", "-Xmx512m"]:
    shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT, exist_ok=True)
    a = ["java", heap] + args[1:]
    t0 = time.time()
    r = subprocess.run(a, capture_output=True, text=True, timeout=900)
    dt = time.time() - t0
    ok = bool(glob.glob(OUT + "/p/*.class"))
    err = ""
    if not ok:
        for l in (r.stdout + r.stderr).splitlines():
            if "OutOfMemory" in l or "rror" in l:
                err = l.strip()[:110]; break
    print(f"heap {heap:10s}: {dt:6.2f}s ok={ok} {err}")

print("\ntotal compiler payload on disk:", sum(
    os.path.getsize(f) for f in glob.glob('/tmp/feas/jars/*.jar')) // 1048576, "MB")
