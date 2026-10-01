package com.davidgcd.backlog.data.share

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The JSON the share server validates (server/lib/shareCore.js): kinds, fields and the single-book rule. */
class BookSharePayloadTest {
    private val dune = ShareBookItem(
        name = "Dune", authors = "Frank Herbert", status = "READ", year = 1965,
        coverUrl = "https://covers.openlibrary.org/b/id/8231856-M.jpg", url = "https://openlibrary.org/works/OL893415W",
        rating = 5, favorite = true,
    )

    @Test
    fun `the books list payload is a books page with every field`() {
        val json = JSONObject(ShareLinkService.booksPayload("Ma liste", listOf(dune), owner = "David"))
        assertEquals("books", json.getString("kind"))
        assertEquals("David", json.getString("owner"))
        val item = json.getJSONArray("items").getJSONObject(0)
        assertEquals("Dune", item.getString("name"))
        assertEquals("Frank Herbert", item.getString("authors"))
        assertEquals("READ", item.getString("status"))
        assertEquals(5, item.getInt("rating"))
        assertTrue(item.getBoolean("favorite"))
        assertEquals(1965, item.getInt("year"))
    }

    @Test
    fun `a single book is a book page with exactly one item, titled by the book`() {
        val json = JSONObject(ShareLinkService.singleBookPayload(dune))
        assertEquals("book", json.getString("kind"))
        assertEquals("Dune", json.getString("title"))
        assertEquals(1, json.getJSONArray("items").length())
    }

    @Test
    fun `missing facts are sent as null, not invented`() {
        val bare = dune.copy(year = null, coverUrl = null, url = null, rating = null, favorite = false)
        val item = JSONObject(ShareLinkService.booksPayload("t", listOf(bare))).getJSONArray("items").getJSONObject(0)
        assertTrue(item.isNull("year") && item.isNull("coverUrl") && item.isNull("url") && item.isNull("rating"))
    }

    @Test
    fun `the library payload carries a books section next to games and movies, and the old call still works`() {
        val json = JSONObject(ShareLinkService.libraryPayload("Ma librairie", emptyList(), emptyList(), "David", listOf(dune)))
        assertEquals("library", json.getString("kind"))
        assertEquals(1, json.getJSONObject("books").getJSONArray("items").length())
        assertEquals(0, json.getJSONObject("games").getJSONArray("items").length())
        // Without books (games and films only) the section is there and empty.
        val old = JSONObject(ShareLinkService.libraryPayload("L", emptyList(), emptyList()))
        assertEquals(0, old.getJSONObject("books").getJSONArray("items").length())
    }
}
