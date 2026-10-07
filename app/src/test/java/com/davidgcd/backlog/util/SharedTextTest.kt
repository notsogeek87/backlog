package com.davidgcd.backlog.util

import com.davidgcd.backlog.model.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {
    @Test
    fun `a TMDB link opens the sheet straight away`() {
        assertEquals(
            SharedTarget.Detail(DetailLink.Movie("movie:603")),
            SharedText.parse("Regarde ça https://www.themoviedb.org/movie/603-the-matrix"),
        )
        assertEquals(SharedTarget.Detail(DetailLink.Movie("tv:1396")), SharedText.parse("https://www.themoviedb.org/tv/1396-breaking-bad?language=fr"))
    }

    @Test
    fun `a Steam link searches the game by the name in the link`() {
        assertEquals(
            SharedTarget.Search(Medium.GAME, "Hades"),
            SharedText.parse("https://store.steampowered.com/app/1145360/Hades/"),
        )
        assertEquals(
            SharedTarget.Search(Medium.GAME, "Dark Souls III"),
            SharedText.parse("https://store.steampowered.com/app/374320/Dark_Souls_III/"),
        )
    }

    @Test
    fun `book links and ISBNs search the books catalogue`() {
        assertEquals(
            SharedTarget.Search(Medium.BOOK, "The Hobbit"),
            SharedText.parse("https://www.goodreads.com/book/show/5907.The_Hobbit"),
        )
        assertEquals(SharedTarget.Search(Medium.BOOK, "9782070368228"), SharedText.parse("978-2-07-036822-8"))
    }

    @Test
    fun `an IMDb link keeps the text sent with it as the query`() {
        assertEquals(SharedTarget.Search(Medium.MOVIE, "Dune 2"), SharedText.parse("Dune 2 https://www.imdb.com/title/tt15239678/"))
        assertNull(SharedText.parse("https://www.imdb.com/title/tt15239678/"))
    }

    @Test
    fun `plain text becomes an any-medium search and blank text is nothing`() {
        assertEquals(SharedTarget.Search(null, "Zelda Tears of the Kingdom"), SharedText.parse("  Zelda Tears of the Kingdom \n"))
        assertNull(SharedText.parse("   "))
        assertNull(SharedText.parse(null))
    }
}
