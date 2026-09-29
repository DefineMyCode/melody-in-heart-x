"""Focused probe: does the Kotlin+Compose compiler run with ONLY the JDK surface
Android actually provides? Adds missing JDK modules one at a time and records the
first error each time, so we can enumerate exactly which JDK APIs are required.
"""
import os, glob, subprocess

W = "/tmp/feas/run4"
TOOLCP = ":".join(sorted(glob.glob("/tmp/feas/jars/*.jar")))
COMPOSE_RT = "/tmp/feas/jars/cra/classes.jar"
PLUGIN = "/tmp/feas/jars/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar"

def attempt(mods, tag):
    outdir = f"/tmp/feas/probe_{tag}"
    os.makedirs(outdir, exist_ok=True)
    args = ["java", "--limit-modules", ",".join(mods), "-cp", TOOLCP,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-Xplugin=" + PLUGIN,
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:liveLiteralsEnabled=false",
            "-no-stdlib", "-classpath", COMPOSE_RT + ":/tmp/feas/jars/kotlin-stdlib-2.0.21.jar",
            "-d", outdir, W + "/src"]
    r = subprocess.run(args, capture_output=True, text=True, timeout=900)
    o = r.stdout + r.stderr
    cause = ""
    for l in o.splitlines():
        s = l.strip()
        if s.startswith("Caused by:") or s.startswith("exception:"):
            cause = s
            break
    ok = bool(glob.glob(outdir + "/p/*.class"))
    return r.returncode, cause, ok

BASE = ["java.base", "java.logging", "java.xml"]   # roughly Android's libcore surface
print("Android-ish baseline (java.base + logging + xml):")
rc, cause, ok = attempt(BASE, "t_base")
print(f"   exit={rc} ok={ok} err={cause}\n")

CHAIN = ["jdk.unsupported", "java.compiler", "java.management", "java.desktop",
         "jdk.compiler", "java.sql", "java.naming"]
cum = list(BASE)
for extra in CHAIN:
    cum.append(extra)
    rc, cause, ok = attempt(cum, "t_" + extra.replace(".", ""))
    status = "SUCCESS" if (rc == 0 and ok) else "fail"
    print(f"[+{extra:16s}] modules={len(cum)} exit={rc} {status}")
    print(f"      err: {cause[:130] if cause else '-'}")
    if rc == 0 and ok:
        print("\n>>> MINIMAL WORKING SET:", ",".join(cum))
        break
