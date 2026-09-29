package com.davidgcd.backlog.data.library

import com.davidgcd.backlog.data.library.GameMatcher.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameMatcherTest {
    private fun c(a: String, b: String) = GameMatcher.classify(a, b)

    @Test fun `identical titles are an exact match`() = assertEquals(Confidence.EXACT, c("Stardew Valley", "Stardew Valley"))

    @Test fun `case, punctuation, accents and trademarks are ignored`() {
        assertEquals(Confidence.EXACT, c("HITMAN™ 3", "Hitman 3"))
        assertEquals(Confidence.EXACT, c("Pokémon Legends", "pokemon legends"))
        assertEquals(Confidence.EXACT, c("The Witcher 3", "Witcher 3"))
    }

    @Test fun `a subtitle variant is proposed, not applied`() =
        assertEquals(Confidence.LIKELY, c("The Witcher 3: Wild Hunt", "The Witcher 3"))

    @Test fun `DOOM and DOOM Eternal are different games`() = assertEquals(Confidence.NONE, c("DOOM", "DOOM Eternal"))

    @Test fun `sequels differing by a number never match`() {
        assertEquals(Confidence.NONE, c("Fallout 3", "Fallout 4"))
        assertEquals(Confidence.NONE, c("Borderlands 2", "Borderlands 3"))
    }

    @Test fun `unrelated titles do not match`() = assertEquals(Confidence.NONE, c("Hades", "Celeste"))

    @Test fun `empty titles never match`() = assertEquals(Confidence.NONE, c("", ""))

    @Test fun `best prefers an exact candidate over a likely one`() {
        val best = GameMatcher.best("The Witcher 3", listOf("The Witcher 3: Wild Hunt", "The Witcher 3")) { it }
        assertEquals("The Witcher 3" to Confidence.EXACT, best)
    }

    @Test fun `best returns null when nothing is close`() =
        assertNull(GameMatcher.best("Hades", listOf("Celeste", "Doom")) { it })
}
