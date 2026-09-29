"""API-faithful stub generator.

For a missing JDK class, reflect over the REAL JDK class and emit a stub with the
same public/protected API surface (fields + methods, correct signatures, default
bodies). This is the strongest possible test of the "ship the JDK in your APK"
path: if even API-faithful stubs can't satisfy the compiler, vendoring is hopeless.
"""
import glob, os, re, shutil, subprocess, sys, json

MODS = ["java.base", "java.logging", "java.xml"]
STUB = "/tmp/feas/stubs3"
TOOLCP = ":".join(sorted(glob.glob("/tmp/feas/jars/*.jar")))
COMPOSE_RT = "/tmp/feas/jars/cra/classes.jar"
PLUGIN = "/tmp/feas/jars/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar"
SRC = "/tmp/feas/run4/src"

# ---- dump the real JDK API of a class, in a FULL-JDK process ----
DUMPER = r'''
import java.lang.reflect.*;
import java.util.*;
public class DumpAPI {
  public static void main(String[] a) throws Exception {
    Class<?> c = Class.forName(a[0]);
    System.out.println("KIND=" + (c.isInterface() ? "interface" : "class"));
    System.out.println("ABSTRACT=" + Modifier.isAbstract(c.getModifiers()));
    for (Field f : c.getFields()) {
      int m = f.getModifiers();
      if (!Modifier.isPublic(m) && !Modifier.isProtected(m)) continue;
      System.out.println("FIELD\t" + Modifier.toString(m) + "\t" + f.getType().getName() + "\t" + f.getName());
    }
    for (Field f : c.getDeclaredFields()) {
      int m = f.getModifiers();
      if (Modifier.isPublic(m) || Modifier.isProtected(m)) continue;
      System.out.println("PRIVFIELD\t" + Modifier.toString(m) + "\t" + f.getType().getName() + "\t" + f.getName());
    }
    for (Method me : c.getMethods()) {
      int m = me.getModifiers();
      if (Modifier.isStatic(m) || Modifier.isAbstract(m)) { /* also report */ }
      StringBuilder p = new StringBuilder();
      for (Class<?> pt : me.getParameterTypes()) {
        if (p.length() > 0) p.append(",");
        p.append(pt.getCanonicalName() == null ? pt.getName() : pt.getCanonicalName());
      }
      System.out.println("METHOD\t" + Modifier.toString(m) + "\t"
        + (me.getReturnType().getCanonicalName() == null ? me.getReturnType().getName() : me.getReturnType().getCanonicalName())
        + "\t" + me.getName() + "\t" + p);
    }
  }
}
'''.strip()

def dump_api(fqcn):
    d = "/tmp/feas/dumper"
    os.makedirs(d, exist_ok=True)
    if not os.path.exists(d + "/DumpAPI.class"):
        open(d + "/DumpAPI.java", "w").write(DUMPER)
        subprocess.run(["javac", "-d", d, d + "/DumpAPI.java"], check=True, capture_output=True)
    r = subprocess.run(["java", "-cp", d, "DumpAPI", fqcn], capture_output=True, text=True)
    if r.returncode != 0:
        return None
    return r.stdout

PRIM_DEFAULT = {
    "int": "0", "long": "0L", "short": "(short)0", "byte": "(byte)0",
    "char": "(char)0", "float": "0f", "double": "0d", "boolean": "false",
    "void": "",
}

def gen_stub(fqcn, api):
    kind = "class"
    abstract = "true"
    fields, methods, privfields = [], [], []
    for line in api.splitlines():
        parts = line.split("\t")
        if parts[0] == "KIND":
            kind = parts[1]; continue
        if parts[0] == "ABSTRACT":
            abstract = parts[1]; continue
        if parts[0] == "FIELD" and len(parts) >= 5:
            fields.append((parts[1], parts[2], parts[3]))
        elif parts[0] == "PRIVFIELD" and len(parts) >= 5:
            privfields.append((parts[1], parts[2], parts[3]))
        elif parts[0] == "METHOD" and len(parts) >= 5:
            methods.append((parts[1], parts[2], parts[3], parts[4]))

    pkg, _, simple = fqcn.rpartition(".")
    nested = "$" in simple
    simple = simple.replace("$", ".")
    body = []
    # private fields the compiler may reach via reflection (e.g. Unsafe.theUnsafe)
    for mods, ftype, fname in privfields:
        init = "null" if ftype not in PRIM_DEFAULT else PRIM_DEFAULT[ftype]
        body.append(f"  private static {ftype} {fname} = {init};")
    for mods, ftype, fname in fields:
        m = mods.replace("abstract", "").replace("native", "").strip()
        extra = "static " if "static" in mods else ""
        body.append(f"  public {extra}{ftype} {fname};" if "final" not in mods
                    else f"  public {extra}final {ftype} {fname} = "
                         + ("null" if ftype not in PRIM_DEFAULT else PRIM_DEFAULT[ftype]) + ";")
    seen = set()
    for mods, rtype, mname, params in methods:
        key = (mname, params, rtype)
        if key in seen:
            continue
        seen.add(key)
        if mname in ("wait", "notify", "notifyAll", "getClass", "hashCode", "equals", "toString", "clone", "finalize"):
            continue
        # synthesise parameter names: reflection does not expose them
        plist = [t for t in params.split(",") if t.strip()]
        decl_params = ", ".join(f"{t} p{i}" for i, t in enumerate(plist))
        static = "static " if "static" in mods else ""
        ret = "" if rtype == "void" else "return " + (PRIM_DEFAULT.get(rtype, "null")) + ";"
        body.append(f"  public {static}{rtype} {mname}({decl_params}) {{ {ret} }}")
    decl = f"public {'abstract ' if (kind=='class' and abstract=='true') else ''}{kind} {simple} {{"
    src = f"package {pkg};\n@SuppressWarnings(\"all\")\n{decl}\n" + "\n".join(body) + "\n}\n"
    return src, simple, nested

def emit(fqcn, api):
    src, simple, nested = gen_stub(fqcn, api)
    pkg, _, _ = fqcn.rpartition(".")
    d = os.path.join(STUB, "src", pkg.replace(".", "/"))
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, simple.split(".")[0] + ".java")
    open(path, "w").write(src)
    return path

def build_jar():
    cls = STUB + "/cls"
    shutil.rmtree(cls, ignore_errors=True); os.makedirs(cls, exist_ok=True)
    files = glob.glob(STUB + "/src/**/*.java", recursive=True)
    if not files:
        return None
    r = subprocess.run(["javac", "-nowarn", "--limit-modules", ",".join(MODS), "-d", cls] + files,
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError("JAVAC_FAIL: " + r.stderr[:800])
    jar = STUB + "/stubs.jar"
    subprocess.run(["jar", "cf", jar, "-C", cls, "."], check=True, capture_output=True)
    return jar

MISSING_RE = re.compile(r"(?:NoClassDefFoundError|ClassNotFoundException|Could not find [^:]*|NoSuchMethodError|IncompatibleClassChangeError)[:\s]*([\w./$]*)")

def run(stubjar):
    outdir = "/tmp/feas/stub3_out"
    shutil.rmtree(outdir, ignore_errors=True); os.makedirs(outdir, exist_ok=True)
    cp = TOOLCP + ((":" + stubjar) if stubjar else "")
    args = ["java", "--limit-modules", ",".join(MODS), "-cp", cp,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-Xplugin=" + PLUGIN,
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:liveLiteralsEnabled=false",
            "-no-stdlib", "-classpath", COMPOSE_RT + ":/tmp/feas/jars/kotlin-stdlib-2.0.21.jar",
            "-d", outdir, SRC]
    r = subprocess.run(args, capture_output=True, text=True, timeout=900)
    return r.returncode, r.stdout + r.stderr, bool(glob.glob(outdir + "/p/*.class"))

# bootstrap: sun.misc.Unsafe cannot be dumped from a restricted graph, use full JDK dump
CLASS_RE = re.compile(r"(?:NoClassDefFoundError|ClassNotFoundException):\s*([\w./$]+)")
FIELD_RE = re.compile(r"Could not find '?([\w]+)'? field in the ([\w./$]+) class")

done = set()
for i in range(1, 41):
    try:
        jar = build_jar()
    except RuntimeError as e:
        print(f"ROUND {i}: {e}"); break
    rc, out, ok = run(jar)
    if rc == 0 and ok:
        print(f"\n>>> SUCCESS with {len(done)} vendored JDK classes")
        print("   ", sorted(done)); sys.exit(0)

    cm = CLASS_RE.search(out)
    fm = FIELD_RE.search(out)
    if cm:
        fq = cm.group(1).replace("/", ".")
        if fq in done:
            print(f"ROUND {i}: recurring {fq}"); break
        api = dump_api(fq)
        if not api:
            print(f"ROUND {i}: cannot dump real API of {fq} -- stop"); break
        emit(fq, api); done.add(fq)
        print(f"ROUND {i:2d}: vendored faithful stub -> {fq}")
        continue
    if fm:
        print(f"ROUND {i:2d}: missing FIELD '{fm.group(1)}' on {fm.group(2)}")
        api = dump_api(fm.group(2).replace("/", "."))
        print("     real API available:", bool(api))
        break
    cause = next((l.strip() for l in out.splitlines()
                  if l.strip().startswith(("Caused by:", "exception:"))), "(none)")
    print(f"ROUND {i:2d}: STOPPED"); print("   ", cause[:250])
    for l in out.splitlines():
        if re.search(r"rror|xception", l): print("    >", l.strip()[:220]); break
    break

print("\n--- vendored", len(done), "classes:", sorted(done))
