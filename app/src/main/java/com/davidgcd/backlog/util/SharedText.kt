package com.davidgcd.backlog.util

import com.davidgcd.backlog.model.Medium

/** Ce que l'app fait d'un texte partagé vers elle : ouvrir directement une fiche, ou lancer une recherche pré-remplie. */
sealed interface SharedTarget {
    data class Detail(val link: DetailLink) : SharedTarget

    /** [medium] null : le média n'est pas reconnu, la recherche croisée trouvera dans les trois catalogues. */
    data class Search(val medium: Medium?, val query: String) : SharedTarget
}

/**
 * « Partager vers Backlog » : un lien Steam, TMDB, IGDB, Goodreads, Open Library, un ISBN ou un simple titre.
 * Un lien TMDB ouvre la fiche tout de suite ; les autres préparent la recherche du bon média avec le nom du jeu,
 * du livre… lu dans le lien.
 */
object SharedText {
    private val url = Regex("""https?://[^\s]+""")
    private const val MAX_QUERY = 120

    fun parse(text: String?): SharedTarget? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val link = url.find(raw)?.value
        if (link != null) fromLink(link, raw.replace(link, " "))?.let { return it }
        val plain = (if (link != null) raw.replace(link, " ") else raw).replace(Regex("\\s+"), " ").trim()
        if (plain.isEmpty()) return null
        Isbn.fromQuery(plain)?.let { return SharedTarget.Search(Medium.BOOK, it) }
        return SharedTarget.Search(null, plain.take(MAX_QUERY))
    }

    private fun fromLink(link: String, rest: String): SharedTarget? {
        val clean = link.trimEnd('.', ',', ')', ']')
        val host = clean.substringAfter("://").substringBefore('/').removePrefix("www.").lowercase()
        val path = clean.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#').trim('/')
        val parts = path.split('/').filter { it.isNotEmpty() }
        return when {
            host.endsWith("themoviedb.org") && parts.size >= 2 && parts[0] in setOf("movie", "tv") -> {
                val id = parts[1].takeWhile { it.isDigit() }.toLongOrNull()
                if (id != null) {
                    SharedTarget.Detail(DetailLink.Movie("${parts[0]}:$id"))
                } else {
                    null
                }
            }
            host.endsWith("steampowered.com") && parts.size >= 3 && parts[0] == "app" -> searchOf(Medium.GAME, parts[2])
            host.endsWith("igdb.com") && parts.size >= 2 && parts[0] == "games" -> searchOf(Medium.GAME, parts[1])
            host.endsWith("goodreads.com") && parts.size >= 3 && parts[0] == "book" ->
                searchOf(Medium.BOOK, parts[2].substringAfter('.').substringAfter('-'))
            host.endsWith("openlibrary.org") && parts.size >= 3 && parts[0] in setOf("works", "books") -> searchOf(Medium.BOOK, parts[2])
            host.endsWith("imdb.com") || host.endsWith("senscritique.com") || host.endsWith("allocine.fr") ->
                rest.trim().takeIf { it.isNotEmpty() }?.let { SharedTarget.Search(Medium.MOVIE, it.take(MAX_QUERY)) }
            else -> null
        }
    }

    private fun searchOf(medium: Medium, slug: String): SharedTarget? {
        val words = slug.replace('_', ' ').replace('-', ' ').trim()
        return words.takeIf { it.isNotEmpty() }?.let { SharedTarget.Search(medium, it) }
    }
}
