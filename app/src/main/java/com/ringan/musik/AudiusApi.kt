package com.ringan.musik

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AudiusApi {
    private const val BASE = "https://api.audius.co/v1"
    private const val APP = "RinganMusik"

    fun streamUrl(id: String) = "$BASE/tracks/$id/stream?app_name=$APP"

    suspend fun trending(): List<Track> =
        fetch("$BASE/tracks/trending?limit=40&app_name=$APP")

    suspend fun search(query: String): List<Track> =
        fetch("$BASE/tracks/search?query=${URLEncoder.encode(query, "UTF-8")}&limit=40&app_name=$APP")

    private suspend fun fetch(url: String): List<Track> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/json")
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<Track> {
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty() || !o.optBoolean("is_streamable", true)) continue

            val art = o.optJSONObject("artwork")
            val cover = art?.optString("480x480")?.takeIf { it.isNotEmpty() }
                ?: art?.optString("150x150")?.takeIf { it.isNotEmpty() }
                ?: o.optString("artwork").takeIf { it.startsWith("http") }

            out += Track(
                id = id,
                title = o.optString("title").ifEmpty { "Tanpa judul" },
                artist = o.optJSONObject("user")?.optString("name").orEmpty(),
                artwork = cover,
                durationSec = o.optInt("duration")
            )
        }
        return out.distinctBy { it.id }
    }
}
