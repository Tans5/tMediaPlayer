package com.tans.tmediaplayer.demo

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tans.tmediaplayer.demo.databinding.AudioItemLayoutBinding
import com.tans.tmediaplayer.demo.databinding.AudiosFragmentBinding
import com.tans.tmediaplayer.demo.glide.MediaImageModel
import com.tans.tmediaplayer.player.tMediaPlayer
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.dialog.dp2px
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import com.tans.tuiutils.mediastore.MediaStoreAudio
import com.tans.tuiutils.mediastore.queryAudioFromMediaStore
import com.tans.tuiutils.view.clicks
import com.tans.tuiutils.view.refreshes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AudiosFragment : BaseCoroutineStateFragment<AudiosFragment.Companion.State>(State()) {

    override val layoutId: Int = R.layout.audios_fragment

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        launch { refreshAudios() }
    }

//    private val player: tMediaPlayer by lazy {
//        tMediaPlayer()
//    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = AudiosFragmentBinding.bind(contentView)
        viewBinding.refreshLayout.refreshes(this, Dispatchers.IO) {
            refreshAudios()
        }
        val glideLoadManager = Glide.with(this@AudiosFragment)
        glideLoadManager.resumeRequests()
        val adapter = SimpleAdapterBuilderImpl<AudioAndLoadModel>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.audio_item_layout),
            dataSource = FlowDataSourceImpl(stateFlow().map { it.audios }),
            dataBinder = DataBinderImpl { (audio, loadModel), view, _ ->
                val itemViewBinding = AudioItemLayoutBinding.bind(view)
                itemViewBinding.titleTv.text = audio.title
                itemViewBinding.artistAlbumTv.text = "${audio.artist}-${audio.album}"
                itemViewBinding.durationTv.text = audio.duration.formatDuration()
                glideLoadManager
                    .load(loadModel)
                    .error(R.drawable.ic_audio)
                    .placeholder(R.drawable.ic_audio)
                    .into(itemViewBinding.audioImgIv)
                itemViewBinding.root.clicks(this) {
//                    player.prepare(audio.file?.canonicalPath ?: "")
//                    player.play()
                   startActivity(PlayerActivity.createIntent(requireActivity(), audio.file?.canonicalPath ?: ""))
                }
            }
        ).build()
        viewBinding.audiosRv.adapter = adapter

        viewBinding.audiosRv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    glideLoadManager.resumeRequests()
                } else {
                    glideLoadManager.pauseRequests()
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.audiosRv) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + requireContext().dp2px(8))
            insets
        }
    }

    private fun refreshAudios() {
        val audios = queryAudioFromMediaStore()
            .sortedBy { it.dateModified }
            .filter { it.file != null }
            .map {
                AudioAndLoadModel(
                    audio = it,
                    loadModel = MediaImageModel(
                        mediaFilePath = it.file?.canonicalPath ?: "",
                        targetPosition = 0L,
                        keyId = it.albumId
                    )
                )
            }
        updateState {
            it.copy(audios = audios)
        }
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        player.release()
//    }

    companion object {
        data class AudioAndLoadModel(
            val audio: MediaStoreAudio,
            val loadModel: MediaImageModel
        )
        data class State(
            val audios: List<AudioAndLoadModel> = emptyList()
        )
    }
}