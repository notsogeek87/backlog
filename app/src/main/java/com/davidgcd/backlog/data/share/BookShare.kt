package com.davidgcd.backlog.data.share

import com.davidgcd.backlog.data.local.BookEntity
import com.davidgcd.backlog.data.repository.toBook
import com.davidgcd.backlog.model.Book
import com.davidgcd.backlog.util.BookImage

/** A saved book as the share page shows it: its status, favorite and note come along. */
fun BookEntity.toShareItem(): ShareBookItem = toBook().toShareItem(this)

/** Any book as the share page shows it; [saved] adds the reader's own status, favorite and note (a search hit has none). */
fun Book.toShareItem(saved: BookEntity? = null) = ShareBookItem(
    name = title,
    authors = authorLine,
    status = saved?.status ?: com.davidgcd.backlog.model.ReadStatus.TO_READ.name,
    year = publishedYear,
    coverUrl = BookImage.sized(coverUrl, 'M'),
    url = catalogUrl,
    rating = saved?.userRating,
    favorite = saved?.isFavorite == true,
)
