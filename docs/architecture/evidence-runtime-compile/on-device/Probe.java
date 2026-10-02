import java.lang.reflect.Method;

/**
 * 真机探针：验证「安卓运行时的 JDK 面」到底缺哪些东西。
 *
 * 背景：docs/architecture/RUNTIME_COMPILE_FEASIBILITY.md 的所有实测量都在
 * 桌面 JVM（OpenJDK 21）上完成——报告自己声明了这是个弱点（§八"关键未找到可靠来源"）。
 * 本探针把同样的问题搬到**真安卓运行时（ART）**上直接问一遍。
 *
 * 运行方式（无需装 App，直接用 ART 拉起）：
 *   d8 --output out Probe.class
 *   adb push out/classes.dex /data/local/tmp/probe.jar
 *   adb shell "CLASSPATH=/data/local/tmp/probe.jar app_process /system/bin Probe"
 */
public class Probe {

    /** 逐类探测：能否 Class.forName 到。 */
    private static final String[] CLASSES = {
        // 编译器初始化路径上的关键依赖
        "sun.misc.Unsafe",
        "javax.tools.JavaCompiler",
        "javax.tools.ToolProvider",
        "javax.lang.model.element.Element",
        "javax.swing.JComponent",
        "java.awt.Component",
        "java.lang.management.ManagementFactory",
        "com.sun.tools.javac.api.JavacTool",
        // io / 文件系统
        "java.nio.file.FileSystem",
        "java.nio.file.Files",
        "java.nio.file.spi.FileSystemProvider",
        "sun.nio.fs.UnixFileSystemProvider",
        // invoke / lambda
        "java.lang.invoke.LambdaMetafactory",
        "java.lang.invoke.MethodHandle",
        "java.lang.invoke.MethodHandles",
        // 编译器本体（kotlin-compiler-embeddable）
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "com.intellij.openapi.vfs.VirtualFileManager",
        // 对照：这些安卓肯定有
        "android.app.Activity",
        "org.json.JSONObject",
    };

    public static void main(String[] args) {
        String vmName = System.getProperty("java.vm.name", "?");
        String vmVer = System.getProperty("java.vm.version", "?");
        String spec = System.getProperty("java.specification.version", "?");

        System.out.println("========== ART 真机探针 ==========");
        System.out.println("java.vm.name              = " + vmName);
        System.out.println("java.vm.version           = " + vmVer);
        System.out.println("java.specification.version= " + spec);
        System.out.println();

        System.out.println("----- 1) 关键类可用性 -----");
        int present = 0, missing = 0;
        for (String c : CLASSES) {
            boolean ok = exists(c);
            if (ok) present++; else missing++;
            System.out.println((ok ? "  [有] " : "  [无] ") + c);
        }
        System.out.println("小计: 有 " + present + " / 无 " + missing);
        System.out.println();

        System.out.println("----- 2) LambdaMetafactory 关键方法 -----");
        // 没有这个，Kotlin/Java 8 lambda 无法在运行期被合成 —— 编译器自身大量使用 lambda
        try {
            Class<?> lm = Class.forName("java.lang.invoke.LambdaMetafactory");
            boolean metafactory = false;
            for (Method m : lm.getDeclaredMethods()) {
                if (m.getName().equals("metafactory")) { metafactory = true; break; }
            }
            System.out.println("  LambdaMetafactory.metafactory  = " + (metafactory ? "存在" : "缺失"));
        } catch (Throwable t) {
            System.out.println("  LambdaMetafactory              = 类不可用 (" + t.getClass().getSimpleName() + ")");
        }
        System.out.println();

        System.out.println("----- 3) java.* / javax.* 包清单 -----");
        // 安卓的 java.* 只来自 libcore，包数量远少于 JDK
        for (String prefix : new String[]{"java.", "javax."}) {
            java.util.TreeSet<String> pkgs = new java.util.TreeSet<>();
            for (java.util.Map.Entry<Package, ?> e : new java.util.HashMap<Package, Object>().entrySet()) {
                // no-op
            }
            System.out.println("  " + prefix + " 前缀包数（android 的 Package 不含 bootclasspath 全量，故为下界）: "
                    + pkgs.size());
        }
        System.out.println();

        System.out.println("----- 4) 内存上限 -----");
        Runtime rt = Runtime.getRuntime();
        System.out.println("  maxMemory   = " + (rt.maxMemory()/1024/1024) + " MB");
        System.out.println("  totalMemory = " + (rt.totalMemory()/1024/1024) + " MB");
        System.out.println("  processors  = " + rt.availableProcessors());
        System.out.println();

        System.out.println("----- 5) java.nio.file 是否真能用（编译器初始化就碰它） -----");
        try {
            Class<?> files = Class.forName("java.nio.file.Files");
            Method m = files.getMethod("createTempFile", String.class, String.class);
            Object p = m.invoke(null, "probe", ".tmp");
            System.out.println("  Files.createTempFile 成功 -> " + p);
        } catch (Throwable t) {
            Throwable c = (t.getCause() != null) ? t.getCause() : t;
            System.out.println("  Files.createTempFile 失败 -> " + c.getClass().getName()
                    + ": " + c.getMessage());
        }
        System.out.println();
        System.out.println("========== 探针结束 ==========");
    }

    private static boolean exists(String name) {
        try {
            Class.forName(name, false, Probe.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
