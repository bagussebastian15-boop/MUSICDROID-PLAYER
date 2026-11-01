package com.ringan.musik

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue

class Library(private val ctx: Context) {
    private val musicDir = File(ctx.filesDir, "music").apply { mkdirs() }
    private val artDir = File(ctx.filesDir, "art").apply { mkdirs() }
    private val indexFile = File(ctx.filesDir, "library.json")

    private val _downloaded = MutableStateFlow(readIndex())
    val downloaded: StateFlow<List<Track>> = _downloaded

    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val queue = ConcurrentLinkedQueue<Track>()

    fun fileIfDownloaded(id: String): File? =
        File(musicDir, "$id.mp3").takeIf { it.exists() && it.length() > 0 }

    fun enqueue(t: Track) {
        if (fileIfDownloaded(t.id) != null || _progress.value.containsKey(t.id)) return
        _progress.update { it + (t.id to 0) }
        queue.add(t)
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
    }

    fun poll(): Track? = queue.poll()

    fun clearQueue() {
        queue.forEach { q -> _progress.update { it - q.id } }
        queue.clear()
    }

    fun fail(title: String) {
        events.tryEmit("Gagal mengunduh: $title")
    }

    fun delete(id: String) {
        File(musicDir, "$id.mp3").delete()
        File(artDir, "$id.jpg").delete()
        _downloaded.update { list -> list.filter { it.id != id } }
        writeIndex()
    }

    suspend fun downloadNow(t: Track) = withContext(Dispatchers.IO) {
        val tmp = File(musicDir, "${t.id}.part")
        val conn = URL(AudiusApi.streamUrl(t.id)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var read = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        ensureActive()
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                _progress.update { it + (t.id to pct) }
                            }
                        }
                    }
                }
            }
            val target = File(musicDir, "${t.id}.mp3")
            if (!tmp.renameTo(target)) throw IOException("Gagal menyimpan file")

            val artFile = t.artwork?.takeIf { it.startsWith("http") }?.let { u ->
                runCatching {
                    val f = File(artDir, "${t.id}.jpg")
                    URL(u).openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    f
                }.getOrNull()
            }
            val saved = t.copy(artwork = artFile?.let { Uri.fromFile(it).toString() } ?: t.artwork)
            _downloaded.update { list -> listOf(saved) + list.filter { it.id != t.id } }
            writeIndex()
        } finally {
            conn.disconnect()
            tmp.delete()
            _progress.update { it - t.id }
        }
    }

    @Synchronized
    private fun writeIndex() {
        val arr = JSONArray()
        _downloaded.value.forEach { arr.put(it.toJson()) }
        indexFile.writeText(arr.toString())
    }

    private fun readIndex(): List<Track> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length())
                .map { Track.fromJson(arr.getJSONObject(it)) }
                .filter { fileIfDownloaded(it.id) != null }
        }.getOrDefault(emptyList())
    }
}
