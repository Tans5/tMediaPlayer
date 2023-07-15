package com.tans.tmediaplayer

import android.util.Log

internal object MediaLog {

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        Log.e(tag, msg, e)
    }
}