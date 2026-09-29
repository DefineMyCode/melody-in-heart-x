"""Refined Android-like platform simulation.

Android's libcore provides: java.base (partial), java.logging, java.xml (partial),
org.w3c/org.xml. It does NOT provide: java.compiler (javax.tools, javax.lang.model,
javax.annotation.processing), java.desktop (javax.swing, java.awt, java.beans),
java.management, com.sun.tools, com.sun.source, jdk.compiler.

We progressively restrict the JDK module graph to find the FIRST hard failure,
reporting exactly which JDK module the Kotlin compiler needs.
"""
import os, glob, subprocess

W = "/tmp/feas/run4"
os.makedirs(W + "/src/p", exist_ok=True)
open(W + "/src/p/Hello.kt", "w").write("""
package p

import androidx.compose.runtime.Composable

@Composable
fun Greeting(name: String) {
    println("hello " + name)
}
""")

COMPILER_JARS = [
    "kotlin-daemon-embeddable-2.0.21.jar", "kotlin-reflect-1.6.10.jar",
    "kotlin-script-runtime-2.0.21.jar", "trove4j-1.0.20200330.jar",
    "annotations-13.0.jar", "kotlinx-coroutines-core-jvm-1.8.1.jar",
    "kotlin-stdlib-2.0.21.jar", "kotlin-compiler-embeddable-2.0.21.jar",
]
TOOLCP = ":".join("/tmp/feas/jars/" + j for j in COMPILER_JARS)
COMPOSE_RT = "/tmp/feas/jars/cra/classes.jar"
PLUGIN = "/tmp/feas/jars/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar"
USERCP = COMPOSE_RT + ":" + "/tmp/feas/jars/kotlin-stdlib-2.0.21.jar"

MODSETS = [
    ("Android-like A: base+logging+xml (no java.compiler/desktop/management)",
     "java.base,java.logging,java.xml"),
    ("Android-like B: base+logging+xml+management (management IS absent on Android, added as control)",
     "java.base,java.logging,java.xml,java.management"),
    ("Android-like C: add java.compiler (javax.tools/javax.lang.model) -- NOT available on Android",
     "java.base,java.logging,java.xml,java.management,java.compiler"),
    ("Full JDK (baseline, for reference)", None),
]

for label, mods in MODSETS:
    outdir = W + "/out_" + str(abs(hash(label)) % 10**6)
    os.makedirs(outdir, exist_ok=True)
    flags = ["--limit-modules", mods] if mods else []
    args = ["java"] + flags + ["-cp", TOOLCP,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-Xplugin=" + PLUGIN,
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:liveLiteralsEnabled=false",
            "-no-stdlib", "-classpath", USERCP, "-d", outdir, W + "/src"]
    print(f"\n{'='*74}\n### {label}")
    r = subprocess.run(args, capture_output=True, text=True, timeout=900)
    print("exit code:", r.returncode)
    o = (r.stdout + r.stderr).strip()
    # extract the first exception / cause lines
    lines = [l for l in o.splitlines() if l.strip()]
    print("\n".join(lines[:14]) if lines else "(no output)")
    prod = glob.glob(outdir + "/p/*.class")
    print("--> produced classes:", [os.path.basename(c) for c in prod])
