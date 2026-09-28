package com.davidgcd.backlog.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSimilarityTest {

    @Test
    fun `identical titles match exactly`() {
        assertTrue(TitleSimilarity.matches("Doom", "Doom"))
        assertTrue(TitleSimilarity.similarity("Doom", "Doom") == 1.0)
    }

    @Test
    fun `case and punctuation differences still match`() {
        assertTrue(TitleSimilarity.matches("doom", "DOOM"))
        assertTrue(TitleSimilarity.matches("The Witcher 3: Wild Hunt", "the witcher 3 wild hunt"))
    }

    @Test
    fun `unrelated titles do not match`() {
        assertFalse(TitleSimilarity.matches("Doom", "Half-Life"))
    }

    @Test
    fun `minor typos still match above threshold`() {
        assertTrue(TitleSimilarity.matches("Elden Ring", "Elden Rng"))
    }
}
