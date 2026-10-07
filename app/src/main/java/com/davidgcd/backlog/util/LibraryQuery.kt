package com.davidgcd.backlog.util

import java.text.Normalizer

/** La recherche « dans ma liste » : insensible à la casse et aux accents, tous les mots de la requête doivent se trouver dans le texte. */
object LibraryQuery {
    private val accents = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(accents, "").lowercase().trim()

    fun matches(query: String, vararg fields: String?): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ") { normalize(it) }
        return words.all { it in haystack }
    }
}
