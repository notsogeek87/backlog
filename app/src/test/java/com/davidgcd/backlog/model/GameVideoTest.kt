package com.davidgcd.backlog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameVideoTest {
    @Test
    fun `a French-titled trailer wins, then any trailer, then the first video`() {
        val fr = GameVideo("fr1", "Bande-annonce de lancement (VF)")
        val en = GameVideo("en1", "Launch Trailer")
        val gameplay = GameVideo("gp1", "Gameplay")
        assertEquals(Trailer("fr1", true), GameVideo.pickTrailer(listOf(gameplay, en, fr)))
        assertEquals(Trailer("en1", false), GameVideo.pickTrailer(listOf(gameplay, en)))
        assertEquals(Trailer("gp1", false), GameVideo.pickTrailer(listOf(gameplay)))
        assertNull(GameVideo.pickTrailer(emptyList()))
        assertNull(GameVideo.pickTrailer(listOf(GameVideo("", "Trailer"))))
    }
}
