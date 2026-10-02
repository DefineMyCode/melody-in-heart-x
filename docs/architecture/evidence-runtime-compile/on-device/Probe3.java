import java.lang.reflect.Method;

/** 打印 sun.misc.Unsafe 里 copyMemory 等方法的**精确签名**，查明为何反射查找失败。 */
public class Probe3 {

    public static void main(String[] args) throws Exception {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        System.out.println("========== Unsafe 方法签名精确对照 ==========");
        System.out.println("目标（IntelliJ 平台要的）: copyMemory(Object,long,Object,long,long)");
        System.out.println();
        System.out.println("安卓实际声明的 copyMemory 重载:");
        boolean exact = false;
        for (Method m : u.getDeclaredMethods()) {
            if (m.getName().equals("copyMemory")) {
                StringBuilder sb = new StringBuilder("  " + m.getName() + "(");
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(ps[i].getSimpleName());
                }
                sb.append(")  mod=").append(java.lang.reflect.Modifier.toString(m.getModifiers()));
                System.out.println(sb);
                if (ps.length == 5 && ps[0] == Object.class && ps[1] == long.class
                        && ps[2] == Object.class && ps[3] == long.class && ps[4] == long.class) {
                    exact = true;
                }
            }
        }
        System.out.println();
        System.out.println("精确匹配 5 参重载存在? " + exact);
        System.out.println();
        System.out.println("----- 直接用反射查找（复现平台的调用方式） -----");
        try {
            Method m = u.getMethod("copyMemory", Object.class, long.class,
                    Object.class, long.class, long.class);
            System.out.println("  getMethod 成功 -> " + m);
        } catch (Throwable t) {
            System.out.println("  getMethod 失败 -> " + t);
        }
        try {
            Method m = u.getDeclaredMethod("copyMemory", Object.class, long.class,
                    Object.class, long.class, long.class);
            System.out.println("  getDeclaredMethod 成功 -> " + m);
        } catch (Throwable t) {
            System.out.println("  getDeclaredMethod 失败 -> " + t);
        }
        System.out.println();
        System.out.println("----- 隐藏 API 限制检测 -----");
        // Android 9+ 对非 SDK 接口有黑白名单；被拦的成员反射调用会抛
        for (String n : new String[]{"copyMemory", "getUnsafe", "allocateMemory"}) {
            try {
                for (Method m : u.getDeclaredMethods()) {
                    if (m.getName().equals(n)) {
                        m.setAccessible(true);
                        System.out.println("  " + n + " setAccessible 成功（未被隐藏 API 拦截）");
                        break;
                    }
                }
            } catch (Throwable t) {
                System.out.println("  " + n + " setAccessible 被拦 -> " + t.getClass().getSimpleName());
            }
        }
        System.out.println();
        System.out.println("========== 结束 ==========");
    }
}
