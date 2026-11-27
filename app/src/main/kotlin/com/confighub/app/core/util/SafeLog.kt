package com.confighub.app.core.util

import android.util.Log
import com.confighub.app.BuildConfig
import com.confighub.app.core.security.SecretMasker

/**
 * The ONLY logging facade allowed in this app: every message passes through
 * [SecretMasker.sanitize], verbose logs are debug-build-only, and no third-party
 * logging/crash SDK exists anywhere in the dependency graph.
 */
object SafeLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, SecretMasker.sanitize(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, SecretMasker.sanitize(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, SecretMasker.sanitize(message), throwable?.let { Exception(SecretMasker.sanitize(it.message ?: it.javaClass.simpleName)) })
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, SecretMasker.sanitize(message), throwable?.let { Exception(SecretMasker.sanitize(it.message ?: it.javaClass.simpleName)) })
    }
}
