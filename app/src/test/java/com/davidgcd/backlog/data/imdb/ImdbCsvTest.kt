package com.davidgcd.backlog.data.imdb

import com.davidgcd.backlog.model.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImdbCsvTest {
    private val ratings = "﻿Const,Your Rating,Date Rated,Title,Original Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors\n" +
        "tt0111161,10,2020-01-02,The Shawshank Redemption,The Shawshank Redemption,https://www.imdb.com/title/tt0111161/,Movie,9.3,142,1994,Drama,2900000,1994-09-23,Frank Darabont\n" +
        "tt0903747,9,2020-02-03,\"Breaking Bad, the show\",Breaking Bad,https://www.imdb.com/title/tt0903747/,TV Series,9.5,49,2008,\"Crime, Drama, Thriller\",2100000,2008-01-20,\n" +
        "tt0000003,7,2020-03-03,Some Game,Some Game,https://www.imdb.com/title/tt0000003/,Video Game,7.0,,2010,Action,10,2010-01-01,\n" +
        "tt0000004,,2020-03-03,Bad Rating Row,Bad,https://x/,Movie,,,,,,,\n"

    @Test
    fun `ratings export is read by header name`() {
        val rows = ImdbCsv.parse(ratings)
        assertEquals(listOf("tt0111161", "tt0903747", "tt0000004"), rows.map { it.title.id })
        val first = rows[0]
        assertEquals(10, first.userRating)
        assertEquals("The Shawshank Redemption", first.title.title)
        assertEquals(TitleKind.MOVIE, first.title.kind)
        assertEquals(1994, first.title.year)
        assertEquals(142, first.title.runtimeMinutes)
        assertEquals(9.3, first.title.rating!!, 0.0001)
        assertEquals("Frank Darabont", first.title.directors)
        val series = rows[1]
        assertEquals("Breaking Bad, the show", series.title.title)
        assertEquals(TitleKind.SERIES, series.title.kind)
        assertEquals(listOf("Crime", "Drama", "Thriller"), series.title.genres)
        assertNull(rows[2].userRating)
    }

    @Test
    fun `watchlist export with a multi-line description`() {
        val csv = "Position,Const,Created,Modified,Description,Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,Release Date,Directors\n" +
            "1,tt1375666,2021-01-01,2021-01-01,\"line one\nline two\",Inception,https://www.imdb.com/title/tt1375666/,Movie,8.8,148,2010,\"Action, Sci-Fi\",2500000,2010-07-16,Christopher Nolan\n" +
            "2,tt0944947,2021-01-01,2021-01-01,,Game of Thrones,https://www.imdb.com/title/tt0944947/,TV Series,9.2,57,2011,\"Action, Adventure\",2200000,2011-04-17,\n"
        val rows = ImdbCsv.parse(csv)
        assertEquals(listOf("tt1375666", "tt0944947"), rows.map { it.title.id })
        assertEquals("line one\nline two", rows[0].title.plot)
        assertNull(rows[0].userRating)
        assertEquals(TitleKind.SERIES, rows[1].title.kind)
    }

    @Test
    fun `recognises an export and refuses a web page`() {
        assertTrue(ImdbCsv.looksLikeExport(ratings))
        assertFalse(ImdbCsv.looksLikeExport("<!DOCTYPE html><html><title>Sign in</title>"))
        assertTrue(ImdbCsv.parse("<html></html>").isEmpty())
        assertTrue(ImdbCsv.parse("").isEmpty())
    }
}
