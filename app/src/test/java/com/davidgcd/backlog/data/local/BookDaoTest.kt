package com.davidgcd.backlog.data.local

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The books table in an in-memory SQLite database, and a check that it leaves games and films alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class BookDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.bookDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun book(key: String, title: String = "Book $key", addedAt: Long = 0) =
        BookEntity(bookKey = key, title = title, addedAt = addedAt, updatedAt = addedAt)

    @Test
    fun `upsert then findById returns the same row with every field`() = runTest {
        val saved = book("isbn:1").copy(
            subtitle = "Sub", authors = "A;B", description = "D", coverUrl = "c", publishedYear = 1965, publisher = "P",
            isbn10 = "i10", isbn13 = "i13", pageCount = 10, languages = "fre", subjects = "S", workId = "OL1W",
            editionId = "OL1M", status = "READ", isFavorite = true, userRating = 3,
        )
        dao.upsert(saved)
        assertEquals(saved, dao.findById("isbn:1"))
        assertNull(dao.findById("isbn:2"))
    }

    @Test
    fun `observeAll lists the newest first and follows updates and deletes`() = runTest {
        dao.upsert(book("a", addedAt = 1))
        dao.upsert(book("b", addedAt = 2))
        assertEquals(listOf("b", "a"), dao.observeAll().first().map { it.bookKey })
        dao.update(dao.findById("a")!!.copy(status = "READING", isFavorite = true))
        assertEquals("READING", dao.observeById("a").first()?.status)
        assertTrue(dao.observeById("a").first()!!.isFavorite)
        dao.delete(dao.findById("b")!!)
        assertEquals(listOf("a"), dao.allBooks().map { it.bookKey })
    }

    @Test
    fun `games and films are untouched by the books table`() = runTest {
        database.gameDao().upsert(GameEntity(igdbId = 7, name = "Hades"))
        dao.upsert(book("x"))
        assertEquals("Hades", database.gameDao().findById(7)?.name)
        assertEquals(1, dao.allBooks().size)
        dao.delete(dao.findById("x")!!)
        assertEquals("Hades", database.gameDao().findById(7)?.name)
    }
}
