package com.davidgcd.backlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailLinkTest {
    @Test fun readsAGame() = assertEquals(DetailLink.Game(1942), DetailLink.parse("game", "1942"))

    @Test fun readsFilmsAndSeriesByTheirTmdbKey() {
        assertEquals(DetailLink.Movie("movie:603"), DetailLink.parse("movie", "movie:603"))
        assertEquals(DetailLink.Movie("tv:1396"), DetailLink.parse("movie", "tv:1396"))
    }

    @Test fun readsABookKey() = assertEquals(DetailLink.Book("isbn:9780141036144"), DetailLink.parse("book", "isbn:9780141036144"))

    @Test fun refusesWhatIsNotADetailPage() {
        assertNull(DetailLink.parse("game", "abc"))
        assertNull(DetailLink.parse("movie", "603"))
        assertNull(DetailLink.parse("movie", "person:603"))
        assertNull(DetailLink.parse("book", ""))
        assertNull(DetailLink.parse("settings", "1"))
    }
}

class DetailPreviewTest {
    @Test fun buildsAShortEncodedQueryAndSkipsEmptyFields() {
        val q = DetailPreview(title = "Amélie & co", year = 2001, image = "/abc.jpg", genres = "", rating = 7.96).query()
        assertEquals("?t=Am%C3%A9lie%20%26%20co&y=2001&c=%2Fabc.jpg&r=8.0", q)
    }

    @Test fun cutsAndCleansTheSynopsis() {
        val q = DetailPreview(title = "X", summary = "a\n\nb ".repeat(300)).query()
        assertEquals(true, q.length < 1100)
        assertEquals(true, q.contains("%E2%80%A6"))
    }

    @Test fun keepsOnlyThePosterPathOfATmdbUrl() {
        assertEquals("/abc.jpg", DetailPreview.posterPath("https://image.tmdb.org/t/p/w500/abc.jpg"))
        assertNull(DetailPreview.posterPath(null))
    }
}
