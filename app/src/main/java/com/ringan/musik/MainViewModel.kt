package com.ringan.musik

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Feed(
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null
)

data class Now(
    val id: String? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val posMs: Long = 0,
    val durMs: Long = 0
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val library = getApplication<App>().library
    private var controller: MediaController? = null
    private var searchJob: Job? = null

    private val _feed = MutableStateFlow(Feed(loading = true))
    val feed: StateFlow<Feed> = _feed.asStateFlow()

    private val _now = MutableStateFlow(Now())
    val now: StateFlow<Now> = _now.asStateFlow()

    val downloaded = library.downloaded
    val progress = library.progress
    val events = library.events

    init {
        connectPlayer()
        load("")
    }

    private fun connectPlayer() {
        val ctx = getApplication<App>().applicationContext
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { c ->
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = sync()
                })
                sync()
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val md = c.mediaMetadata
        _now.value = Now(
            id = item?.mediaId,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artwork = md.artworkUri?.toString(),
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            posMs = c.currentPosition.coerceAtLeast(0L),
            durMs = if (c.duration > 0) c.duration else 0L
        )
    }

    fun refreshPosition() = sync()

    fun load(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _feed.value = Feed(loading = true)
            _feed.value = try {
                val list = if (query.isBlank()) AudiusApi.trending() else AudiusApi.search(query.trim())
                Feed(tracks = list, error = if (list.isEmpty()) "Tidak ada hasil" else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Feed(error = "Tidak bisa terhubung. Periksa internet Anda.")
            }
        }
    }

    fun play(list: List<Track>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(list.map { it.toMediaItem(library.fileIfDownloaded(it.id)) }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        sync()
    }

    fun download(t: Track) = library.enqueue(t)

    fun delete(t: Track) = library.delete(t.id)

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
