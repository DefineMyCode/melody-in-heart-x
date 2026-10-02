import zipfile, struct, collections

ANDROID_JAR = "/opt/android-sdk/platforms/android-36/android.jar"

def cp_classes(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        return []
    count = struct.unpack_from(">H", data, 8)[0]
    i, idx, utf8, out = 10, 1, {}, []
    while idx < count:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack_from(">H", data, i + 1)[0]
            utf8[idx] = data[i + 3:i + 3 + ln].decode("utf-8", "replace"); i += 3 + ln
        elif tag == 7:
            n = utf8.get(struct.unpack_from(">H", data, i + 1)[0])
            if n: out.append(n)
            i += 3
        elif tag in (8, 16, 19, 20): i += 3
        elif tag == 15: i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): i += 5
        elif tag in (5, 6): i += 9; idx += 1
        else: return out
        idx += 1
    return out

z = zipfile.ZipFile(ANDROID_JAR)
acls = set(n[:-6] for n in z.namelist() if n.endswith(".class"))

for path in ["/tmp/feas/jars/kotlin-compiler-embeddable-2.0.21.jar",
             "/tmp/feas/jars/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar"]:
    jz = zipfile.ZipFile(path)
    classes_ref = collections.Counter()   # distinct classes referencing
    for n in jz.namelist():
        if not n.endswith(".class"): continue
        for c in set(cp_classes(jz.read(n)) or []):
            c = c.lstrip("[")
            base = c.split(";")[0].split("[")[0]
            root = base.split("/")[0]
            if root in ("java", "javax", "jdk", "sun") or base.startswith("com/sun/"):
                classes_ref[base] += 1
    missing = {k: v for k, v in classes_ref.items() if k not in acls}
    print(f"\n===== {path.split('/')[-1]}")
    print(f"  distinct JDK classes referenced: {len(classes_ref)}; absent from android.jar: {len(missing)}")
    print(f"  total compiler classes whose CP references a missing JDK class (sum): {sum(missing.values())}")
    grp = collections.Counter()
    for k, v in missing.items(): grp["/".join(k.split("/")[:3])] += v
    print("  worst packages (distinct classes referencing missing):")
    for k, v in grp.most_common(14): print(f"     {k:42s} {v}")
    top = sorted(missing.items(), key=lambda x: -x[1])[:12]
    print("  top individual offenders:")
    for k, v in top: print(f"     {v:5d} classes reference  {k}")
