package com.tans.tmediaplayer.demo.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter
import com.bumptech.glide.load.resource.bitmap.BitmapResource

class BitmapResourceDecoder : ResourceDecoder<Bitmap, Bitmap> {

    private val bitmapPool by lazy {
        BitmapPoolAdapter()
    }

    override fun handles(source: Bitmap, options: Options): Boolean = true

    override fun decode(
        source: Bitmap,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap> {
        return BitmapResource(source, bitmapPool)
    }

}