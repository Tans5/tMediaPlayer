package com.tans.tmediaplayer

class NativeLib {

    /**
     * A native method that is implemented by the 'tmediaplayer' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'tmediaplayer' library on application startup.
        init {
            System.loadLibrary("tmediaplayer")
        }
    }
}