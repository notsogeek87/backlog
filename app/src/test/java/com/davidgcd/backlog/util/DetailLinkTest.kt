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
