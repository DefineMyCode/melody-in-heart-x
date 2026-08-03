package cn.com.dcsgo.mihx.core.common

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

interface AppLogger {
    fun debug(tag: String, message: String)
    fun info(tag: String, message: String)
    fun warning(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
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
}

class AndroidAppLogger(
    private val debugLoggingEnabled: Boolean,
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

internal fun String.redactSensitiveValuesForLog(): String {
    return replace(Regex("""content://[^\s)]+"""), "content://<redacted>")
        .replace(Regex("""file://[^\s)]+"""), "file://<redacted>")
        .replace(Regex("""(?i)\b([0-9A-F]{2}:){5}[0-9A-F]{2}\b"""), "<bluetooth-address-redacted>")
        .replace(Regex("""(?i)\b(device(Name)?|bluetooth(Name)?|bt(Name)?)=("[^"]*"|'[^']*'|[^\s,)]+)""")) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        .replace(Regex("""(?i)[A-Z]:\\[^\s)]+"""), "<path-redacted>")
        .replace(Regex("""(?<![:/])/[^\s)]+"""), "/<path-redacted>")
}
