"""Precise CONSTANT_Class reference scan of a jar against android.jar."""
import zipfile, struct, collections, sys, re

ANDROID_JAR = "/opt/android-sdk/platforms/android-36/android.jar"

def parse_cp_classes(data):
    """Yield internal class names from the constant pool (CONSTANT_Class entries)."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return
    count = struct.unpack_from(">H", data, 8)[0]
    i = 10
    utf8 = {}
    classes = []
    # first pass: collect Utf8
    idx = 1
    offs = {}
    while idx < count:
        offs[idx] = i
        tag = data[i]
        if tag == 1:
            ln = struct.unpack_from(">H", data, i + 1)[0]
            utf8[idx] = data[i + 3:i + 3 + ln].decode("utf-8", "replace")
            i += 3 + ln
        elif tag in (7, 8, 16, 19, 20):
            i += 3
        elif tag == 15:
            i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            i += 5
        elif tag in (5, 6):
            i += 9
            idx += 1
        else:
            return
        idx += 1
    # second pass: resolve Class entries
    idx = 1
    i = 10
    while idx < count:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack_from(">H", data, i + 1)[0]
            i += 3 + ln
        elif tag == 7:
            name_idx = struct.unpack_from(">H", data, i + 1)[0]
            n = utf8.get(name_idx)
            if n:
                classes.append(n)
            i += 3
        elif tag in (8, 16, 19, 20):
            i += 3
        elif tag == 15:
            i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            i += 5
        elif tag in (5, 6):
            i += 9
            idx += 1
        else:
            return
        idx += 1
    return classes

def android_set():
    z = zipfile.ZipFile(ANDROID_JAR)
    return set(n[:-6] for n in z.namelist() if n.endswith(".class"))

def scan(path, acls):
    z = zipfile.ZipFile(path)
    ref = collections.Counter()
    for n in z.namelist():
        if not n.endswith(".class"):
            continue
        cs = parse_cp_classes(z.read(n)) or []
        for c in cs:
            if c and c[0] in "$[":
                c = c.lstrip("[")
            base = c.split(";")[0].split("[")[0]
            if not base:
                continue
            root = base.split("/")[0]
            if root in ("java", "javax", "jdk", "sun", "com"):
                if root == "com" and not base.startswith("com/sun/"):
                    continue
                ref[base] += 1
    missing = {k: v for k, v in ref.items() if k not in acls}
    present = {k: v for k, v in ref.items() if k in acls}
    return ref, missing, present

acls = android_set()
print(f"android.jar (android-36) classes: {len(acls)}")
for path in [
    "/tmp/feas/jars/kotlin-compiler-embeddable-2.0.21.jar",
    "/tmp/feas/jars/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar",
]:
    ref, missing, present = scan(path, acls)
    name = path.split("/")[-1]
    print(f"\n===== {name}")
    print(f"  JDK-platform class refs: {len(ref)}  | present in android.jar: {len(present)}  | MISSING: {len(missing)}")
    grp = collections.Counter()
    for k, v in missing.items():
        grp["/".join(k.split("/")[:3])] += 1
    print("  missing grouped by package:")
    for k, v in grp.most_common(20):
        print(f"     {k:40s} {v} distinct classes")
    print("  ALL missing, sorted by ref count (top 40):")
    for k, v in sorted(missing.items(), key=lambda x: -x[1])[:40]:
        print(f"     {v:5d}  {k}")
    crit = ["java/lang/instrument/ClassFileTransformer", "java/lang/management/ManagementFactory",
            "javax/tools/ToolProvider", "javax/tools/JavaCompiler", "javax/lang/model/element/Element",
            "javax/xml/stream/XMLStreamReader", "javax/swing/JComponent", "sun/misc/Unsafe"]
    print("  critical probes:")
    for c in crit:
        print(f"     {c:50s} in_android={c in acls:5} used={c in ref}")
