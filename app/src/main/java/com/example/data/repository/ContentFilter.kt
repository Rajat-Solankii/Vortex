package com.example.data.repository

import com.example.data.model.MediaItem
import com.example.data.model.MediaItemDto

/**
 * Strict safe-content filter applied to ALL TMDB responses before they are
 * cached in Room or surfaced in the UI.
 *
 * Motivation: TMDB's `include_adult=false` flag is insufficient because
 * fan-tagging can bypass it, especially for anime/ecchi titles.
 *
 * Strategy (three independent checks, ANY match → rejected):
 *   1. `adult == true` flag from the TMDB response itself
 *   2. Blocked keyword appears in the overview or title
 *   3. Title is on the explicit deny-list of known adult titles
 */
object ContentFilter {

    private val BLOCKED_OVERVIEW_KEYWORDS = listOf(
        "hentai", "ecchi", "erotica", "sexual content", "nudity",
        "uncensored", "adult animation", "porn", "lust", "brothel",
        "succubus", "prostitute", "slave", "explicit", "fetish",
        "masturbat", "orgasm", "erotic"
    )

    // Keywords allowed in overviews but immediately suspicious in titles
    private val BLOCKED_TITLE_KEYWORDS = listOf(
        "hentai", "ecchi", "porn", "harem", "sex ", "sex,",
        "adult animation", "erotica"
    )

    private val BLOCKED_TITLES = setOf(
        "overflow",
        "sweet punishment",
        "redo of healer",
        "yosuga no sora",
        "high school dxd",
        "shimoneta",
        "prison school",
        "joshiochi",
        "kiss x sis",
        "monster musume",
        "to love ru",
        "testament of sister new devil",
        "harem in the labyrinth",
        "world's end harem",
        "valkyrie drive",
        "interspecies reviewers",
        "peter grill",
        "mushoku tensei",
        "domestic girlfriend",
        "souryo to majiwaru",
        "shikiyoku",
        "harem camp",
        "skirt no naka",
        "amakano",
        "tsurezure children",
        "nuki doki",
        "oni chichi",
        "baku ane",
        "imouto paradise",
        "stringendo",
        "bible black",
        "words worth",
        "sexfriend",
        "sex friend"
    )

    /**
     * Filter a list of [MediaItemDto] (raw network layer) before caching.
     */
    fun filterDtos(items: List<MediaItemDto>): List<MediaItemDto> = items.filter { dto ->
        val title = (dto.title ?: dto.name ?: "").lowercase()
        val overview = (dto.overview ?: "").lowercase()
        !isBlocked(title, overview, dto.adult ?: false)
    }

    /**
     * Filter a list of [MediaItem] (domain layer) before surfacing in the UI.
     * Used as a second safety net on data coming out of Room.
     */
    fun filterDomain(items: List<MediaItem>): List<MediaItem> = items.filter { item ->
        val title = item.title.lowercase()
        val overview = item.overview.lowercase()
        !isBlocked(title, overview, item.adult)
    }

    // ─── private helpers ───────────────────────────────────────────────────────

    private fun isBlocked(title: String, overview: String, adultFlag: Boolean): Boolean {
        if (adultFlag) return true
        if (BLOCKED_OVERVIEW_KEYWORDS.any { overview.contains(it) }) return true
        if (BLOCKED_TITLE_KEYWORDS.any { title.contains(it) }) return true
        if (BLOCKED_TITLES.any { title.contains(it) }) return true
        return false
    }
}
