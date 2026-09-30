package com.davidgcd.backlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImdbImageTest {
    @Test
    fun `asks IMDb for a sized poster`() {
        assertEquals(
            "https://m.media-amazon.com/images/M/MV5B@._V1_UY400_.jpg",
            ImdbImage.poster("https://m.media-amazon.com/images/M/MV5B@._V1_.jpg"),
        )
        assertEquals(
            "https://m.media-amazon.com/images/M/MV5B@._V1_UY240_.jpg",
            ImdbImage.poster("https://m.media-amazon.com/images/M/MV5B@._V1_QL75_UX380_CR0,1,380,562_.jpg", height = 240),
        )
    }

    @Test
    fun `leaves other urls alone, blank is no poster`() {
        assertEquals("https://x/y.jpg", ImdbImage.poster("https://x/y.jpg"))
        assertNull(ImdbImage.poster(null))
        assertNull(ImdbImage.poster(""))
    }
}
