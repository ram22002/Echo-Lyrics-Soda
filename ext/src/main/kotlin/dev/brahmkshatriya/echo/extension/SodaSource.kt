package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import java.util.UUID

/**
 * Douyin Soda (package com.luna.music). The Android client sends signed device parameters and
 * the audit received HTTP 200 with an empty body, so this provider is BLOCKED. The request
 * shape is kept faithful to the reference client.
 */
internal class SodaSource : LyricsSource {

    private val throttle = Throttle(800)

    override suspend fun search(track: Track, settings: Settings?): List<Lyrics> {
        val query = track.query()
        if (query.title.isBlank() || query.artist.isBlank()) return emptyList()
        throttle.await()
        val keyword = Http.encode(query.artist + " " + track.title)
        val url = SEARCH_URL +
            "?device_platform=android&os=android&ssmix=a" +
            "&cdid=" + UUID.randomUUID() +
            "&channel=xiaomi_8478_64&aid=386088&app_name=luna" +
            "&version_code=100198030&version_name=19.8.0&manifest_version_code=100198030" +
            "&update_version_code=100198030&resolution=1080*1920&dpi=480" +
            "&device_type=ABR-AL80&device_brand=HUAWEI&language=zh&os_api=35&os_version=15" +
            "&ac=wifi&device_model=ABR-AL80&tz_name=Asia/Shanghai&tz_offset=28800" +
            "&package=com.luna.music&sim_region=cn" +
            "&iid=" + UUID.randomUUID() + "&device_id=" + UUID.randomUUID() +
            "&_rticket=" + System.currentTimeMillis() +
            "&q=" + keyword + "&cursor=0&count=20"
        val body = runCatching { Http.get(url, HEADERS) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        val groups = runCatching { Http.json(body).obj()?.arr("result_groups").objects() }.getOrNull()
            ?: return emptyList()

        val candidates = mutableListOf<Pair<Candidate, String>>()
        for (group in groups) {
            for (item in group.arr("data").objects()) {
                val trackObject = item.obj("entity")?.obj("track") ?: continue
                val id = trackObject.str("id") ?: continue
                val title = trackObject.str("name") ?: continue
                val artist = trackObject.arr("artists").objects().firstOrNull()?.str("name").orEmpty()
                val duration = ((trackObject.long("duration") ?: 0L) / 1000)
                candidates += Candidate(title, artist, "", duration) to id
            }
        }

        return candidates.filter { Match.accept(query, it.first) }
            .sortedByDescending { Match.score(query, it.first) }
            .take(6)
            .map { (candidate, id) ->
                Lyrics(
                    id = "soda:$id",
                    title = candidate.title,
                    subtitle = candidate.artist + " • Soda",
                    lyrics = null,
                    extras = mapOf("id" to id),
                )
            }
    }

    override suspend fun load(lyrics: Lyrics, settings: Settings?): Lyrics {
        val id = lyrics.extras["id"] ?: throw LyricsException("Soda candidate is missing its track id")
        throttle.await()
        val url = DETAIL_URL + "?track_id=" + Http.encode(id) + "&device_platform=web"
        val root = Http.json(Http.get(url, WEB_HEADERS)).obj()
            ?: throw LyricsException("Empty Soda response")
        val content = root.obj("lyric")?.str("content")
            ?: throw LyricsException("Soda has no lyrics for this track")
        val type = root.obj("lyric")?.str("type").orEmpty()
        val lines = if (type.equals("krc", ignoreCase = true)) {
            parseSodaTimed(content).ifEmpty { Lrc.parse(content) }
        } else {
            Lrc.parse(content)
        }
        if (lines.isNotEmpty()) return lyrics.copy(lyrics = timed(lines, lines.any { it.words.size > 1 }))
        val plainLines = Lrc.parsePlain(content)
        if (plainLines.isNotEmpty()) return lyrics.copy(lyrics = plain(plainLines.joinToString("\n")))
        throw LyricsException("Soda lyric payload could not be parsed")
    }

    companion object {
        private const val SEARCH_URL = "https://api.qishui.com/luna/search/track"
        private const val DETAIL_URL = "https://beta-luna.douyin.com/luna/h5/seo_track"
        private val HEADERS = mapOf(
            "User-Agent" to "Luna/19.8.0 (Linux; Android 15; ABR-AL80)",
            "Accept" to "application/json",
        )
        private val WEB_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36",
            "Accept" to "application/json",
        )

        fun settings(): List<Setting> = emptyList()
    }
}

private val SODA_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
private val SODA_WORD = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")

/** KRC/YRC style timing used by the Luna payload. */
internal fun parseSodaTimed(text: String): List<Line> {
    val lines = mutableListOf<Line>()
    for (raw in text.lines()) {
        val match = SODA_LINE.find(raw.trim()) ?: continue
        val start = match.groupValues[1].toLongOrNull() ?: continue
        val duration = match.groupValues[2].toLongOrNull() ?: 0L
        val body = match.groupValues[3]
        val words = SODA_WORD.findAll(body).mapNotNull { word ->
            val wordStart = word.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val wordDuration = word.groupValues[2].toLongOrNull() ?: 0L
            val content = word.groupValues[3]
            if (content.isEmpty()) null else Word(wordStart, wordStart + maxOf(wordDuration, 1L), content)
        }.toList()
        val textContent = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim()
        else SODA_WORD.replace(body, "").trim()
        if (textContent.isEmpty()) continue
        lines += Line(start, start + maxOf(duration, 1L), textContent, words)
    }
    return Lrc.withEnds(lines.sortedBy { it.startMs }, 0L)
}
