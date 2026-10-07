package com.davidgcd.backlog.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationsTest {
    @Test
    fun `migrations form one unbroken chain from version 1 to the current schema`() {
        val steps = Migrations.ALL.map { it.startVersion to it.endVersion }
        assertEquals((1..9).map { it to it + 1 }, steps)
    }

    @Test
    fun `the books table arrives with the 8 to 9 step, after the games and movies ones`() {
        assertTrue(Migrations.MIGRATION_8_9.startVersion == 8 && Migrations.MIGRATION_8_9.endVersion == 9)
    }
}
