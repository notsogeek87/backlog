package com.davidgcd.backlog.data.repository

import com.davidgcd.backlog.data.local.BookDao
import com.davidgcd.backlog.data.local.BookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory BookDao for repository tests — no Room, no Robolectric needed. */
class FakeBookDao(initial: List<BookEntity> = emptyList()) : BookDao {
    private val state = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<BookEntity>> = state.map { list -> list.sortedByDescending { it.addedAt } }

    override suspend fun allBooks(): List<BookEntity> = state.value.sortedByDescending { it.addedAt }

    override suspend fun findById(bookKey: String): BookEntity? = state.value.firstOrNull { it.bookKey == bookKey }

    override fun observeById(bookKey: String): Flow<BookEntity?> = state.map { list -> list.firstOrNull { it.bookKey == bookKey } }

    override suspend fun upsert(book: BookEntity) {
        state.value = state.value.filterNot { it.bookKey == book.bookKey } + book
    }

    override suspend fun update(book: BookEntity) {
        state.value = state.value.map { if (it.bookKey == book.bookKey) book else it }
    }

    override suspend fun setRank(bookKey: String, rank: Int?) {
        state.value = state.value.map { if (it.bookKey == bookKey) it.copy(userRank = rank) else it }
    }

    override suspend fun delete(book: BookEntity) {
        state.value = state.value.filterNot { it.bookKey == book.bookKey }
    }
}
