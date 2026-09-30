package com.davidgcd.backlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbImageTest {
    @Test
    fun `resizes a stored poster to a TMDB width`() {
        assertEquals("https://image.tmdb.org/t/p/w185/abc.jpg", TmdbImage.poster("https://image.tmdb.org/t/p/w500/abc.jpg", width = 185))
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", TmdbImage.poster("https://image.tmdb.org/t/p/original/abc.jpg"))
    }

    @Test
    fun `stores a poster path as a full url, ignores anything else`() {
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", TmdbImage.stored("/abc.jpg"))
        assertNull(TmdbImage.stored(null))
        assertNull(TmdbImage.stored(""))
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", TmdbImage.poster("/abc.jpg", width = 342))
    }

    @Test
    fun `leaves other urls alone, blank is no poster`() {
        assertEquals("https://x/y.jpg", TmdbImage.poster("https://x/y.jpg"))
        assertNull(TmdbImage.poster(null))
        assertNull(TmdbImage.poster(""))
    }
}
