package com.tans.tmediaplayer.demo.glide

import android.graphics.Bitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.tans.tmediaplayer.frameloader.tMediaFrameLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit

class MediaImageModelLoader : ModelLoader<MediaImageModel, Bitmap> {
    override fun buildLoadData(
        model: MediaImageModel,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(model, MediaImageDataFetcher(model))
    }

    override fun handles(model: MediaImageModel): Boolean {
        return true
    }

    class MediaImageDataFetcher(private val model: MediaImageModel) : DataFetcher<Bitmap> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
            loadExecutor.execute {
                loadSemaphore.acquire()
                if (!loadFailHistory.containsKey(model)) {
                    val bitmap = tMediaFrameLoader.loadMediaFileFrame(model.mediaFilePath, model.targetPosition)
                    if (bitmap != null) {
                        callback.onDataReady(bitmap)
                    } else {
                        loadFailHistory[model] = Unit
                        callback.onLoadFailed(Exception("tMediaFrameLoader load $model fail."))
                    }
                } else {
                    callback.onLoadFailed(Exception("tMediaFrameLoader load $model fail."))
                }
                loadSemaphore.release()
            }
        }

        override fun cleanup() {  }

        override fun cancel() {  }

        override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

        override fun getDataSource(): DataSource = DataSource.REMOTE
    }

    companion object {
        class Factory : ModelLoaderFactory<MediaImageModel, Bitmap> {
            override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaImageModel, Bitmap> {
                return MediaImageModelLoader()
            }

            override fun teardown() {}
        }

        private const val MAX_CONCURRENT_JOBS = 5

        // Max 5 load jobs.
        private val loadSemaphore: Semaphore by lazy {
            Semaphore(MAX_CONCURRENT_JOBS)
        }

        private val loadFailHistory: ConcurrentHashMap<MediaImageModel, Unit> by lazy {
            ConcurrentHashMap()
        }

        private val loadExecutor: Executor by lazy {
            ThreadPoolExecutor(
                0, MAX_CONCURRENT_JOBS,
                60L, TimeUnit.SECONDS,
                SynchronousQueue(),
                {
                    Thread(it, "MediaImageModelLoader")
                },
                CallerRunsPolicy()
            )
        }
    }
}