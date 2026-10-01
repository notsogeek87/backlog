package com.davidgcd.backlog.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {
    @Test
    fun `validates ISBN-13 and ISBN-10 check digits`() {
        assertTrue(Isbn.isValid13("9782070368228"))
        assertFalse(Isbn.isValid13("9782070368229"))
        assertTrue(Isbn.isValid10("207036822X"))
        assertTrue(Isbn.isValid10("080442957X"))
        assertFalse(Isbn.isValid10("2070368221"))
    }

    @Test
    fun `normalize strips hyphens and spaces and rejects garbage`() {
        assertEquals("9782070368228", Isbn.normalize("978-2-07-036822-8"))
        assertEquals("9782070368228", Isbn.normalize(" 978 2 07 036822 8 "))
        assertEquals("080442957X", Isbn.normalize("0-8044-2957-x"))
        assertNull(Isbn.normalize("123"))
        assertNull(Isbn.normalize("9782070368229"))
        assertNull(Isbn.normalize(null))
    }

    @Test
    fun `converts between ISBN-10 and ISBN-13`() {
        assertEquals("9782070368228", Isbn.toIsbn13("207036822X"))
        assertEquals("207036822X", Isbn.toIsbn10("9782070368228"))
        assertEquals("9780804429573", Isbn.toIsbn13("080442957X"))
        assertEquals("080442957X", Isbn.toIsbn10("9780804429573"))
        // Only 978- ISBN-13s have an ISBN-10 twin.
        assertNull(Isbn.toIsbn10("9791032305645"))
    }

    @Test
    fun `a query is an ISBN only when it is nothing but an ISBN`() {
        assertEquals("9782070368228", Isbn.fromQuery("9782070368228"))
        assertEquals("9782070368228", Isbn.fromQuery("978-2-07-036822-8"))
        assertEquals("9782070368228", Isbn.fromQuery("isbn:9782070368228"))
        assertEquals("9782070368228", Isbn.fromQuery("ISBN 9782070368228"))
        assertNull(Isbn.fromQuery("1984"))
        assertNull(Isbn.fromQuery("Dune"))
        assertNull(Isbn.fromQuery("Dune 9782070368228"))
        assertNull(Isbn.fromQuery("9782070368229"))
    }
}
