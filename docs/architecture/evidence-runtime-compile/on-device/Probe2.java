import java.lang.reflect.Method;
import java.util.TreeSet;

/**
 * 精确定位：安卓的 sun.misc.Unsafe 到底缺哪些方法。
 *
 * 背景：真机跑 K2JVMCompiler 时，越过了报告预测的 VirtualFileManagerImpl 崩点，
 * 最终停在：
 *   java.lang.NoSuchMethodException: copyMemory [Object, long, Object, long, long]
 *   at com.intellij.util.ConcurrentLongObjectHashMap.<clinit>
 *
 * 也就是说 class 级探测（"sun.misc.Unsafe 存在"）**不足以**判断可用性——
 * 类存在但方法缺失。本探针把方法级差异列出来。
 */
public class Probe2 {

    public static void main(String[] args) {
        System.out.println("========== sun.misc.Unsafe 方法级探针 ==========");
        try {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            System.out.println("类存在: " + u.getName());
            System.out.println("实现包: " + (u.getProtectionDomain() != null
                    && u.getProtectionDomain().getCodeSource() != null
                    ? u.getProtectionDomain().getCodeSource().getLocation() : "?"));
            System.out.println();

            TreeSet<String> decl = new TreeSet<>();
            for (Method m : u.getDeclaredMethods()) decl.add(m.getName());
            System.out.println("已声明方法数: " + decl.size());

            // 编译器/IntelliJ 平台实际要用的方法
            String[] needed = {
                "copyMemory", "allocateMemory", "freeMemory", "setMemory",
                "objectFieldOffset", "arrayBaseOffset", "arrayIndexScale",
                "getObject", "putObject", "getLong", "putLong", "getInt", "putInt",
                "getByte", "putByte", "compareAndSwapObject", "compareAndSwapLong",
                "compareAndSwapInt", "getAndAddInt", "getAndAddLong",
                "loadFence", "storeFence", "fullFence",
                "invokeCleaner", "allocateInstance", "ensureClassInitialized",
                "throwException", "getUnsafe", "addressSize", "pageSize",
            };
            System.out.println();
            System.out.println("----- 平台关键方法 -----");
            int have = 0, miss = 0;
            for (String n : needed) {
                boolean ok = decl.contains(n);
                if (ok) have++; else miss++;
                System.out.println((ok ? "  [有] " : "  [无] ") + n);
            }
            System.out.println("小计: 有 " + have + " / 无 " + miss);

            System.out.println();
            System.out.println("----- 实际声明的方法全清单 -----");
            StringBuilder sb = new StringBuilder();
            for (String n : decl) sb.append(n).append(' ');
            System.out.println("  " + sb);
        } catch (Throwable t) {
            System.out.println("探针失败: " + t);
        }
        System.out.println();
        System.out.println("========== 结束 ==========");
    }
}
