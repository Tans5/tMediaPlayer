package com.tans.tmediaplayer.player.renderer

import java.util.concurrent.Executors

enum class RendererHandlerMsg {
    RequestRender,
}

internal val renderCallbackExecutor by lazy {
    Executors.newSingleThreadExecutor {
        Thread(it, "tMP_RenderCallback")
    }
}