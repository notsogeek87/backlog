package com.davidgcd.backlog.data.imdb

import com.davidgcd.backlog.model.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImdbParsersTest {

    @Test
    fun `suggestions keep titles and drop people, games and episodes`() {
        val json = """
            {"d":[
              {"i":{"height":1,"imageUrl":"https://m.media-amazon.com/images/M/x._V1_.jpg","width":1},"id":"tt1375666","l":"Inception","q":"feature","qid":"movie","s":"Leonardo DiCaprio","y":2010},
              {"id":"nm0000138","l":"Leonardo DiCaprio","s":"Actor"},
              {"id":"tt0903747","l":"Breaking Bad","qid":"tvSeries","y":2008,"s":"Bryan Cranston"},
              {"id":"tt0000001","l":"Some Game","qid":"videoGame","y":2010},
              {"id":"tt0000002","l":"An Episode","qid":"tvEpisode","y":2011}
            ],"q":"inception","v":1}
        """.trimIndent()
        val result = ImdbParsers.parseSuggestions(json)
        assertEquals(listOf("tt1375666", "tt0903747"), result.map { it.id })
        assertEquals(TitleKind.MOVIE, result[0].kind)
        assertEquals(2010, result[0].year)
        assertEquals("https://m.media-amazon.com/images/M/x._V1_.jpg", result[0].posterUrl)
        assertEquals(TitleKind.SERIES, result[1].kind)
        assertNull(result[1].posterUrl)
    }

    @Test
    fun `suggestions on garbage give an empty list`() {
        assertTrue(ImdbParsers.parseSuggestions("<html>nope</html>").isEmpty())
        assertTrue(ImdbParsers.parseSuggestions("""{"d":[]}""").isEmpty())
    }

    @Test
    fun `title page reads the JSON-LD block`() {
        val html = """
            <html><head><script type="application/ld+json">{"@context":"https://schema.org","@type":"Movie","url":"https://www.imdb.com/title/tt0111161/","name":"Les Évadés","image":"https://m.media-amazon.com/images/M/y._V1_.jpg","description":"Two imprisoned men bond &amp; the &quot;warden&apos;s&quot; secrets.","aggregateRating":{"@type":"AggregateRating","ratingCount":3000000,"bestRating":10,"worstRating":1,"ratingValue":9.3},"contentRating":"R","genre":["Drama"],"datePublished":"1994-10-14","duration":"PT2H22M","director":[{"@type":"Person","name":"Frank Darabont"}],"actor":[{"@type":"Person","name":"Tim Robbins"},{"@type":"Person","name":"Morgan Freeman"}]}</script></head></html>
        """.trimIndent()
        val title = ImdbParsers.parseTitlePage(html)
        assertNotNull(title)
        title!!
        assertEquals("tt0111161", title.id)
        assertEquals("Les Évadés", title.title)
        assertEquals(TitleKind.MOVIE, title.kind)
        assertEquals(1994, title.year)
        assertEquals(9.3, title.rating!!, 0.0001)
        assertEquals(142, title.runtimeMinutes)
        assertEquals(listOf("Drama"), title.genres)
        assertEquals("Frank Darabont", title.directors)
        assertEquals("Tim Robbins, Morgan Freeman", title.cast)
        assertEquals("Two imprisoned men bond & the \"warden's\" secrets.", title.plot)
        assertNotNull(title.releaseDate)
    }

    @Test
    fun `title page tells a series and accepts a single genre string`() {
        val html = """<script type="application/ld+json">{"@type":"TVSeries","url":"https://www.imdb.com/title/tt0903747/","name":"Breaking Bad","genre":"Crime, Drama, Thriller","datePublished":"2008-01-20"}</script>"""
        val title = ImdbParsers.parseTitlePage(html)!!
        assertEquals(TitleKind.SERIES, title.kind)
        assertEquals(listOf("Crime", "Drama", "Thriller"), title.genres)
    }

    @Test
    fun `title page without JSON-LD is null, id falls back to the requested one`() {
        assertNull(ImdbParsers.parseTitlePage("<html>captcha</html>"))
        val html = """<script type="application/ld+json">{"@type":"Movie","name":"No Url"}</script>"""
        assertEquals("tt1234567", ImdbParsers.parseTitlePage(html, fallbackId = "tt1234567")!!.id)
    }

    @Test
    fun `chart reads a JSON-LD item list`() {
        val html = """<script type="application/ld+json">{"@type":"ItemList","itemListElement":[
            {"@type":"ListItem","item":{"@type":"Movie","url":"https://www.imdb.com/title/tt0111161/","name":"Les Évadés","image":"https://x/y.jpg","description":"d","aggregateRating":{"ratingValue":9.3},"genre":"Drama","duration":"PT2H22M"}},
            {"@type":"ListItem","item":{"@type":"TVSeries","url":"https://www.imdb.com/title/tt0903747/","name":"Breaking Bad"}}
        ]}</script>"""
        val chart = ImdbParsers.parseChart(html)
        assertEquals(listOf("tt0111161", "tt0903747"), chart.map { it.id })
        assertEquals(9.3, chart[0].rating!!, 0.0001)
        assertEquals(TitleKind.SERIES, chart[1].kind)
    }

    @Test
    fun `chart falls back to the page's next data`() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"pageData":{"chartTitles":{"edges":[
            {"node":{"id":"tt0111161","titleText":{"text":"The Shawshank Redemption"},"titleType":{"id":"movie"},"releaseYear":{"year":1994},
              "primaryImage":{"url":"https://x/p.jpg"},"ratingsSummary":{"aggregateRating":9.3},"runtime":{"seconds":8520},
              "titleGenres":{"genres":[{"genre":{"text":"Drama"}}]}}}
        ]}}}}}</script>"""
        val chart = ImdbParsers.parseChart(html)
        assertEquals(1, chart.size)
        val t = chart[0]
        assertEquals("The Shawshank Redemption", t.title)
        assertEquals(1994, t.year)
        assertEquals(142, t.runtimeMinutes)
        assertEquals(listOf("Drama"), t.genres)
        assertEquals("https://x/p.jpg", t.posterUrl)
    }

    @Test
    fun `chart of nothing is empty`() {
        assertTrue(ImdbParsers.parseChart("<html></html>").isEmpty())
    }

    @Test
    fun `durations`() {
        assertEquals(148, ImdbParsers.parseIsoDurationMinutes("PT2H28M"))
        assertEquals(45, ImdbParsers.parseIsoDurationMinutes("PT45M"))
        assertEquals(120, ImdbParsers.parseIsoDurationMinutes("PT2H"))
        assertNull(ImdbParsers.parseIsoDurationMinutes("PT"))
        assertNull(ImdbParsers.parseIsoDurationMinutes(""))
        assertNull(ImdbParsers.parseIsoDurationMinutes("2h"))
    }

    @Test
    fun `dates`() {
        assertEquals(1_281_657_600L, ImdbParsers.parseDate("2010-08-13"))
        assertEquals(1_281_657_600L, ImdbParsers.parseDate("2010-8-13"))
        assertNull(ImdbParsers.parseDate("2010"))
        assertNull(ImdbParsers.parseDate("soon"))
        assertNull(ImdbParsers.parseDate(null))
    }

    @Test
    fun `user and watchlist ids`() {
        assertEquals("ur12345678", ImdbParsers.userIdFromUrl("https://www.imdb.com/user/ur12345678/?ref_=nv_usr_prof_2"))
        assertNull(ImdbParsers.userIdFromUrl("https://www.imdb.com/registration/signin"))
        assertEquals("ls098765432", ImdbParsers.watchlistIdFrom("https://www.imdb.com/list/ls098765432/", ""))
        assertEquals("ls055555555", ImdbParsers.watchlistIdFrom("https://www.imdb.com/user/ur1/watchlist", """<a href="/list/ls055555555/export">Export</a>"""))
        assertEquals("ls044444444", ImdbParsers.watchlistIdFrom("https://x/", """{"predefinedList":{"id":"ls044444444","name":"WATCHLIST"}}"""))
        assertNull(ImdbParsers.watchlistIdFrom("https://www.imdb.com/user/ur1/watchlist", "<html></html>"))
    }
}
