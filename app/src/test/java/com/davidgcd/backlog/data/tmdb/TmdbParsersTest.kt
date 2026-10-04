package com.davidgcd.backlog.data.tmdb

import com.davidgcd.backlog.model.TitleKey
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.Trailer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbParsersTest {
    private val genres = mapOf(28 to "Action", 878 to "Science-Fiction", 18 to "Drame")

    @Test
    fun `genres`() {
        assertEquals(genres.filterKeys { it == 28 }, TmdbParsers.parseGenres("""{"genres":[{"id":28,"name":"Action"}]}"""))
        assertTrue(TmdbParsers.parseGenres("nope").isEmpty())
    }

    @Test
    fun `search drops people and reads both kinds`() {
        val json = """{"page":1,"total_pages":1,"results":[
          {"media_type":"movie","id":27205,"title":"Inception","release_date":"2010-07-15","poster_path":"/inc.jpg","overview":"Un voleur.","vote_average":8.369,"vote_count":35000,"genre_ids":[28,878]},
          {"media_type":"tv","id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20","poster_path":null,"vote_average":8.9,"vote_count":12000,"genre_ids":[18]},
          {"media_type":"person","id":6193,"name":"Leonardo DiCaprio"},
          {"media_type":"movie","id":1,"title":""}
        ]}"""
        val items = TmdbParsers.parseList(json, forcedKind = null, genreNames = genres)
        assertEquals(listOf("movie:27205", "tv:1396"), items.map { it.title.id })
        val inception = items[0].title
        assertEquals(TitleKind.MOVIE, inception.kind)
        assertEquals(2010, inception.year)
        assertEquals("https://image.tmdb.org/t/p/w500/inc.jpg", inception.posterUrl)
        assertEquals(listOf("Action", "Science-Fiction"), inception.genres)
        assertEquals(8.369, inception.rating!!, 0.0001)
        assertNull(items[0].userRating)
        val bb = items[1].title
        assertEquals(TitleKind.SERIES, bb.kind)
        assertEquals("Breaking Bad", bb.title)
        assertNull(bb.posterUrl)
    }

    @Test
    fun `an unrated title has no rating, not zero`() {
        val json = """{"results":[{"id":5,"title":"New","release_date":"","vote_average":0.0,"vote_count":0}]}"""
        val t = TmdbParsers.parseList(json, TitleKind.MOVIE, genres).single().title
        assertNull(t.rating)
        assertNull(t.year)
        assertNull(t.releaseDate)
    }

    @Test
    fun `rated list carries the user's rating rounded to 1-10`() {
        val json = """{"total_pages":3,"results":[{"id":603,"title":"Matrix","rating":9.0},{"id":604,"title":"Reloaded","rating":7.5},{"id":605,"title":"Half","rating":0.5}]}"""
        val items = TmdbParsers.parseList(json, TitleKind.MOVIE, emptyMap())
        assertEquals(listOf(9, 8, 1), items.map { it.userRating })
        assertEquals(3, TmdbParsers.totalPages(json))
        assertEquals(1, TmdbParsers.totalPages("garbage"))
    }

    @Test
    fun `movie details with credits`() {
        val json = """{"id":27205,"title":"Inception","release_date":"2010-07-15","runtime":148,"overview":"Un voleur.","poster_path":"/inc.jpg",
          "vote_average":8.4,"vote_count":100,"genres":[{"id":28,"name":"Action"},{"id":878,"name":"Science-Fiction"}],
          "credits":{"cast":[{"name":"Leonardo DiCaprio"},{"name":"Elliot Page"}],"crew":[{"name":"Christopher Nolan","job":"Director"},{"name":"Hans Zimmer","job":"Composer"}]}}"""
        val t = TmdbParsers.parseDetails(json, TitleKind.MOVIE)
        assertNotNull(t)
        t!!
        assertEquals("movie:27205", t.id)
        assertEquals(148, t.runtimeMinutes)
        assertEquals("Christopher Nolan", t.directors)
        assertEquals("Leonardo DiCaprio, Elliot Page", t.cast)
        assertEquals(listOf("Action", "Science-Fiction"), t.genres)
        assertEquals("https://www.themoviedb.org/movie/27205", t.tmdbUrl)
    }

    @Test
    fun `series details use creators and the first episode runtime`() {
        val json = """{"id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20","episode_run_time":[47,45],"created_by":[{"name":"Vince Gilligan"}],"genres":[{"id":18,"name":"Drame"}]}"""
        val t = TmdbParsers.parseDetails(json, TitleKind.SERIES)!!
        assertEquals("tv:1396", t.id)
        assertEquals(47, t.runtimeMinutes)
        assertEquals("Vince Gilligan", t.directors)
        assertEquals("https://www.themoviedb.org/tv/1396", t.tmdbUrl)
    }

    @Test
    fun `credits carry photos, directors first`() {
        val json = """{"id":27205,"title":"Inception","credits":{"cast":[{"id":6193,"name":"Leonardo DiCaprio","character":"Cobb","profile_path":"/leo.jpg"},{"name":"Elliot Page","character":"Ariadne","profile_path":null}],
          "crew":[{"name":"Christopher Nolan","job":"Director","profile_path":"/nolan.jpg"},{"name":"Hans Zimmer","job":"Composer"}]}}"""
        val people = TmdbParsers.parseCredits(json, TitleKind.MOVIE)
        assertEquals(listOf("Christopher Nolan", "Leonardo DiCaprio", "Elliot Page"), people.map { it.name })
        assertEquals(listOf(true, false, false), people.map { it.isDirector })
        assertEquals("/nolan.jpg", people[0].photoPath)
        assertEquals("Cobb", people[1].role)
        assertEquals("https://www.themoviedb.org/person/6193", people[1].tmdbUrl)
        assertNull(people[0].tmdbUrl)
        assertNull(people[2].photoPath)
    }

    @Test
    fun `series credits list the creators first`() {
        val json = """{"id":1396,"name":"Breaking Bad","created_by":[{"name":"Vince Gilligan","profile_path":"/vg.jpg"}],"credits":{"cast":[{"name":"Bryan Cranston","character":"Walter White"}]}}"""
        val people = TmdbParsers.parseCredits(json, TitleKind.SERIES)
        assertEquals(listOf("Vince Gilligan", "Bryan Cranston"), people.map { it.name })
        assertEquals(emptyList<Any>(), TmdbParsers.parseCredits("nope", TitleKind.MOVIE))
    }

    @Test
    fun `details of garbage are null`() {
        assertNull(TmdbParsers.parseDetails("nope", TitleKind.MOVIE))
        assertNull(TmdbParsers.parseDetails("""{"status_code":34}""", TitleKind.MOVIE))
    }

    @Test
    fun `auth payloads`() {
        assertEquals("tok", TmdbParsers.stringField("""{"success":true,"request_token":"tok"}""", "request_token"))
        assertNull(TmdbParsers.stringField("""{"success":false}""", "session_id"))
        assertEquals(42L to "david", TmdbParsers.parseAccount("""{"id":42,"username":"david"}"""))
        assertNull(TmdbParsers.parseAccount("""{}"""))
    }

    @Test
    fun `dates`() {
        assertEquals(1_281_657_600L, TmdbParsers.parseDate("2010-08-13"))
        assertNull(TmdbParsers.parseDate("2010"))
        assertNull(TmdbParsers.parseDate(""))
        assertNull(TmdbParsers.parseDate(null))
    }

    @Test
    fun `title keys`() {
        assertEquals("tv:5", TitleKey.of(TitleKind.SERIES, 5))
        assertEquals(TitleKind.MOVIE, TitleKey.kind("movie:9"))
        assertNull(TitleKey.kind("tt0111161"))
        assertEquals(9L, TitleKey.tmdbId("movie:9"))
        assertNull(TitleKey.tmdbId("movie:x"))
    }

    private val providersJson = """{"id":27205,"results":{
      "US":{"link":"https://www.themoviedb.org/movie/27205/watch?locale=US","flatrate":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.jpg","display_priority":1}]},
      "FR":{"link":"https://www.themoviedb.org/movie/27205/watch?locale=FR",
        "flatrate":[{"provider_id":337,"provider_name":"Disney Plus","logo_path":"/d.jpg","display_priority":5},{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.jpg","display_priority":1}],
        "rent":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/a.jpg","display_priority":3}],
        "buy":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/a.jpg","display_priority":3},{"provider_id":3,"provider_name":"Google Play Movies","logo_path":null,"display_priority":4}],
        "free":[{"provider_id":700,"provider_name":"Arte","logo_path":"/ar.jpg","display_priority":2}],
        "ads":[{"provider_id":700,"provider_name":"Arte","logo_path":"/ar.jpg","display_priority":2},{"provider_id":701,"provider_name":"TF1+","logo_path":"/t.jpg","display_priority":6}]}}}"""

    @Test
    fun `watch providers for France are split by way of watching`() {
        val fr = TmdbParsers.parseWatchProviders(providersJson, "FR")!!
        assertEquals(listOf("Netflix", "Disney Plus"), fr.subscription.map { it.name })
        assertEquals(listOf("Apple TV"), fr.rent.map { it.name })
        assertEquals(listOf("Apple TV", "Google Play Movies"), fr.buy.map { it.name })
        assertEquals(listOf("Arte", "TF1+"), fr.free.map { it.name })
        assertEquals("https://image.tmdb.org/t/p/w92/n.jpg", fr.subscription[0].logoUrl)
        assertNull(fr.buy[1].logoUrl)
        assertEquals("https://www.themoviedb.org/movie/27205/watch?locale=FR", fr.link)
    }

    @Test
    fun `no offers in the region means null`() {
        assertNull(TmdbParsers.parseWatchProviders(providersJson, "DE"))
        assertNull(TmdbParsers.parseWatchProviders("""{"results":{"FR":{"link":"x"}}}""", "FR"))
        assertNull(TmdbParsers.parseWatchProviders("nope", "FR"))
    }

    @Test
    fun `an actor's filmography lists what they played in, newest first, without talk shows`() {
        val json = """{"name":"Leonardo DiCaprio","profile_path":"/leo.jpg","combined_credits":{
          "cast":[
            {"media_type":"movie","id":27205,"title":"Inception","release_date":"2010-07-15","vote_average":8.3,"vote_count":100,"genre_ids":[28]},
            {"media_type":"movie","id":597,"title":"Titanic","release_date":"1997-11-18","vote_average":7.9,"vote_count":100,"genre_ids":[18]},
            {"media_type":"tv","id":9,"name":"Un late show","first_air_date":"2015-01-01","genre_ids":[10767]},
            {"media_type":"movie","id":27205,"title":"Inception","release_date":"2010-07-15","genre_ids":[28]}
          ],
          "crew":[{"media_type":"movie","id":1,"title":"Producteur seulement","job":"Producer","release_date":"2020-01-01"}]}}"""
        val person = TmdbParsers.parsePersonFilmography(json, asDirector = false, genreNames = genres)!!
        assertEquals("Leonardo DiCaprio", person.name)
        assertEquals("/leo.jpg", person.photoPath)
        assertEquals(listOf("movie:27205", "movie:597"), person.titles.map { it.id })
    }

    @Test
    fun `a director's filmography keeps only the films they directed`() {
        val json = """{"name":"Christopher Nolan","combined_credits":{
          "cast":[{"media_type":"movie","id":3,"title":"Caméo","release_date":"2000-01-01"}],
          "crew":[
            {"media_type":"movie","id":27205,"title":"Inception","release_date":"2010-07-15","job":"Director"},
            {"media_type":"movie","id":157336,"title":"Interstellar","release_date":"2014-11-05","job":"Director"},
            {"media_type":"movie","id":157336,"title":"Interstellar","release_date":"2014-11-05","job":"Producer"}
          ]}}"""
        val person = TmdbParsers.parsePersonFilmography(json, asDirector = true, genreNames = genres)!!
        assertEquals(listOf("movie:157336", "movie:27205"), person.titles.map { it.id })
        assertNull(TmdbParsers.parsePersonFilmography("nope", asDirector = true, genreNames = genres))
    }

    @Test
    fun `trailer prefers a French YouTube trailer, else another language, else null`() {
        val json = """{"results":[
          {"site":"YouTube","type":"Teaser","key":"teaser","iso_639_1":"fr"},
          {"site":"Vimeo","type":"Trailer","key":"vimeo","iso_639_1":"fr"},
          {"site":"YouTube","type":"Trailer","key":"en1","iso_639_1":"en","official":true},
          {"site":"YouTube","type":"Trailer","key":"fr1","iso_639_1":"fr","official":false}]}"""
        assertEquals(Trailer("fr1", isFrench = true), TmdbParsers.parseTrailer(json))
        val onlyEn = """{"results":[{"site":"YouTube","type":"Trailer","key":"en1","iso_639_1":"en"}]}"""
        assertEquals(Trailer("en1", isFrench = false), TmdbParsers.parseTrailer(onlyEn))
        assertEquals("https://www.youtube.com/watch?v=en1", TmdbParsers.parseTrailer(onlyEn)!!.url)
        assertNull(TmdbParsers.parseTrailer("""{"results":[]}"""))
        assertNull(TmdbParsers.parseTrailer("nope"))
    }
}
