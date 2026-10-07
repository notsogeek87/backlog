package com.davidgcd.backlog.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryTest {
    @Test
    fun `case and accents are ignored`() {
        assertTrue(LibraryQuery.matches("zelda", "The Legend of Zelda"))
        assertTrue(LibraryQuery.matches("ete", "Été indien"))
        assertTrue(LibraryQuery.matches("ÉTÉ", "ete"))
    }

    @Test
    fun `every word of the query must be found, in any order`() {
        assertTrue(LibraryQuery.matches("souls dark", "Dark Souls III"))
        assertFalse(LibraryQuery.matches("dark elden", "Dark Souls III"))
    }

    @Test
    fun `several fields are searched together and a blank query matches everything`() {
        assertTrue(LibraryQuery.matches("tolkien hobbit", "The Hobbit", null, "J. R. R. Tolkien"))
        assertTrue(LibraryQuery.matches("  ", "anything"))
    }
}
