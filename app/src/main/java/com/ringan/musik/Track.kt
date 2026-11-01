package com.ringan.musik

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONObject
import java.io.File

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?,
    val durationSec: Int = 0
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("artwork", artwork ?: JSONObject.NULL)
        .put("dur", durationSec)

    fun toMediaItem(local: File?): MediaItem {
        val uri = if (local != null) Uri.fromFile(local) else Uri.parse(AudiusApi.streamUrl(id))
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artwork?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    companion object {
        fun fromJson(o: JSONObject) = Track(
            id = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            artwork = if (o.isNull("artwork")) null else o.optString("artwork").ifEmpty { null },
            durationSec = o.optInt("dur")
        )
    }
}
