package cn.com.dcsgo.mihx.core.common.log

import android.util.Log

/** Lightweight logging facade used everywhere. Phase 6 may route to a real backend. */
object AppLogger {
    fun d(tag: String, message: String) = Log.d(tag, message)
    fun w(tag: String, message: String) = Log.w(tag, message)
    fun e(tag: String, throwable: Throwable? = null, message: String) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
