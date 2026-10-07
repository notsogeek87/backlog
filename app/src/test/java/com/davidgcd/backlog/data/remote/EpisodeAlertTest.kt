package com.davidgcd.backlog.data.remote

import com.davidgcd.backlog.data.tmdb.EpisodeInfo
import com.davidgcd.backlog.data.tmdb.EpisodeRules
import com.davidgcd.backlog.data.tmdb.TmdbParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeAlertTest {
    private val day = 86_400L
    private val now = 1_700_000_000L

    private fun episode(airedDaysAgo: Long, season: Int = 2, number: Int = 5) = EpisodeInfo(season, number, now - airedDaysAgo * day, "Titre")

    @Test
    fun `parses the last and next episodes of a series`() {
        val json = """{"last_episode_to_air":{"season_number":2,"episode_number":5,"air_date":"2024-03-01","name":"Cinq"},
            "next_episode_to_air":{"season_number":2,"episode_number":6,"air_date":"2024-03-08","name":"Six"}}"""
        val episodes = TmdbParsers.parseEpisodes(json)!!
        assertEquals("S02E05", episodes.last!!.label)
        assertEquals("Cinq", episodes.last!!.name)
        assertEquals("S02E06", episodes.next!!.label)
    }

    @Test
    fun `a series with no episode data gives nulls, garbage gives nothing`() {
        val episodes = TmdbParsers.parseEpisodes("""{"last_episode_to_air":null}""")!!
        assertNull(episodes.last)
        assertNull(episodes.next)
        assertNull(TmdbParsers.parseEpisodes("nope"))
    }

    @Test
    fun `a recent unannounced episode is announced`() {
        assertTrue(EpisodeRules.shouldNotify(episode(airedDaysAgo = 1), null, now))
        assertTrue(EpisodeRules.shouldNotify(episode(airedDaysAgo = 0), "S02E04", now))
    }

    @Test
    fun `an episode already announced, an old one, or one with no date is not`() {
        assertFalse(EpisodeRules.shouldNotify(episode(1), "S02E05", now))
        assertFalse("a first run must not announce last month's episode", EpisodeRules.shouldNotify(episode(30), null, now))
        assertFalse(EpisodeRules.shouldNotify(EpisodeInfo(1, 1, null, null), null, now))
        assertFalse(EpisodeRules.shouldNotify(null, null, now))
    }
}
