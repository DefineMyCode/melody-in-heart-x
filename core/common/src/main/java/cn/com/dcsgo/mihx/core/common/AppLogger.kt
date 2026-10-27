package cn.com.dcsgo.mihx.core.common

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

interface AppLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warning(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)

    /**
     * 性能痕迹通道——与 debug 日志开关**解耦**。
     *
     * 评审 2026-10-27 · M-20：原先 `PerformanceTrace` 走 `AppLog.info`，
     * 而 `AndroidAppLogger.info` 被 `debugLoggingEnabled`（= `BuildConfig.DEBUG`）
     * 拦住，导致 KDoc 承诺的「release 白名单开关」在 release 下**一条都出不来**：
     * `allow()` 只决定「是否计算」，不决定「是否输出」。
     *
     * 现在性能痕迹走本方法：是否输出由调用方（`PerformanceTrace.shouldTrace`，
     * 即 `isEnabled || allowList.contains(operation)`）判定，本方法只负责写。
     * 因此 release 下 allowlist 的操作会真正落到平台日志。
     */
    fun performance(tag: String, message: String)
}

object AppLog : AppLogger {
    @Volatile
    private var delegate: AppLogger = AndroidAppLogger(debugLoggingEnabled = false)

    fun install(logger: AppLogger) {
        delegate = logger
    }

    override fun debug(tag: String, message: String) {
        delegate.debug(tag, message)
    }

    override fun info(tag: String, message: String) {
        delegate.info(tag, message)
    }

    override fun warning(tag: String, message: String, throwable: Throwable?) {
        delegate.warning(tag, message, throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        delegate.error(tag, message, throwable)
    }

    override fun performance(tag: String, message: String) {
        delegate.performance(tag, message)
    }
}

class AndroidAppLogger(
    private val debugLoggingEnabled: Boolean,
    /**
     * 性能痕迹通道是否写平台日志。
     *
     * 默认 **false**：`AppLog` 的默认委托（单测、未 install 时的早期启动）用它，
     * 避免在 JVM 单测里撞上未 mock 的 `android.util.Log`。
     * 生产环境由 `MelodyApplication` / `LoggerModule` 显式打开（release 也要开——
     * 白名单机制的价值正在于此）。
     */
    private val performanceLoggingEnabled: Boolean = false,
) : AppLogger {
    override fun debug(tag: String, message: String) {
        if (debugLoggingEnabled) Log.d(tag, message.redactSensitiveValuesForLog())
    }

    override fun info(tag: String, message: String) {
        if (debugLoggingEnabled) Log.i(tag, message.redactSensitiveValuesForLog())
    }

    override fun warning(tag: String, message: String, throwable: Throwable?) {
        if (debugLoggingEnabled) {
            Log.w(tag, buildMessage(message, throwable, includeThrowable = true))
        }
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, buildMessage(message, throwable, includeThrowable = debugLoggingEnabled))
    }

    /**
     * 性能痕迹：**不受** `debugLoggingEnabled` 约束，只受 `performanceLoggingEnabled` 约束。
     *
     * release 下普通日志全关，但 allowlisted 的性能操作必须可见——否则线上
     * 播放链路的问题无从定位。是否输出某个操作由 [PerformanceTrace.shouldTrace]
     * 判定（`isEnabled || allowList.contains(operation)`）。
     */
    override fun performance(tag: String, message: String) {
        if (!performanceLoggingEnabled) return
        Log.i(tag, message.redactSensitiveValuesForLog())
    }

    private fun buildMessage(
        message: String,
        throwable: Throwable?,
        includeThrowable: Boolean,
    ): String {
        val redactedMessage = message.redactSensitiveValuesForLog()
        if (throwable == null || !includeThrowable) return redactedMessage
        return "$redactedMessage\n${throwable.redactedStackTraceString()}"
    }
}

internal fun Throwable.redactedStackTraceString(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString().redactSensitiveValuesForLog()
}

// ── 脱敏模式：文件级常量，各编译一次 ──
//
// 评审 2026-10-27 · M-19：原先每个 `Regex(...)` 都是**新构造**——每次调用都要
// 重新解析模式并编译一个 `java.util.regex.Pattern`。模式编译比匹配本身贵一到
// 两个数量级，而本函数每条日志调用一次、`PerformanceTrace.log` 里每个 metadata
// 值还要再调一次。
//
// 已核查：6 个模式均无嵌套量词，lookbehind 为定宽，**不存在灾难性回溯风险**。
private val CONTENT_URI_RE = Regex("""content://[^\s)]+""")
private val FILE_URI_RE = Regex("""file://[^\s)]+""")
private val BLUETOOTH_MAC_RE = Regex("""(?i)\b([0-9A-F]{2}:){5}[0-9A-F]{2}\b""")
private val DEVICE_NAME_RE =
    Regex("""(?i)\b(device(Name)?|bluetooth(Name)?|bt(Name)?)=("[^"]*"|'[^']*'|[^\s,)]+)""")
private val WINDOWS_PATH_RE = Regex("""(?i)[A-Z]:\\[^\s)]+""")
private val POSIX_PATH_RE = Regex("""(?<![:/])/[^\s)]+""")

internal fun String.redactSensitiveValuesForLog(): String {
    return replace(CONTENT_URI_RE, "content://<redacted>")
        .replace(FILE_URI_RE, "file://<redacted>")
        .replace(BLUETOOTH_MAC_RE, "<bluetooth-address-redacted>")
        .replace(DEVICE_NAME_RE) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        .replace(WINDOWS_PATH_RE, "<path-redacted>")
        .replace(POSIX_PATH_RE, "/<path-redacted>")
}
