package com.tans.tmediaplayer.demo.glide

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.bumptech.glide.load.Encoder
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.BufferedOutputStream
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool
import com.bumptech.glide.load.resource.bitmap.BitmapEncoder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class BitmapEncoder(private val arrayPool: ArrayPool) : Encoder<Bitmap> {

    override fun encode(bitmap: Bitmap, file: File, options: Options): Boolean {
        val format: CompressFormat = getFormat(bitmap, options)
        val quality: Int = options.get(BitmapEncoder.COMPRESSION_QUALITY)!!
        var success = false
        var os: OutputStream? = null
        try {
            os = FileOutputStream(file)
            os = BufferedOutputStream(os, arrayPool)
            bitmap.compress(format, quality, os)
            os.close()
            success = true
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (e: IOException) {
                    // Do nothing.
                }
            }
        }
        return success
    }

    private fun getFormat(bitmap: Bitmap, options: Options): CompressFormat {
        val format = options.get(BitmapEncoder.COMPRESSION_FORMAT)
        return format
            ?: if (bitmap.hasAlpha()) {
                CompressFormat.PNG
            } else {
                CompressFormat.JPEG
            }
    }
}