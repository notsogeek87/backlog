package com.davidgcd.backlog.util

/**
 * ISBN helpers — the one place that knows how an ISBN is written, validated and converted, so the
 * duplicate check, the search box and the CSV import all agree on what "the same ISBN" means.
 */
object Isbn {
    /** Digits only (plus a trailing X for ISBN-10): `978-2-07-036822-8` → `9782070368228`. */
    fun clean(raw: String?): String? {
        val value = raw?.filter { it.isDigit() || it == 'x' || it == 'X' }?.uppercase()
        return value?.takeIf { it.isNotEmpty() }
    }

    fun isValid10(isbn: String): Boolean {
        if (isbn.length != 10) return false
        var sum = 0
        for (i in 0 until 10) {
            val c = isbn[i]
            val digit = when {
                c.isDigit() -> c - '0'
                c == 'X' && i == 9 -> 10
                else -> return false
            }
            sum += digit * (10 - i)
        }
        return sum % 11 == 0
    }

    fun isValid13(isbn: String): Boolean {
        if (isbn.length != 13 || !isbn.all { it.isDigit() }) return false
        return checkDigit13(isbn.take(12)) == isbn[12] - '0'
    }

    /** A valid ISBN-10 or ISBN-13 written in any common way (hyphens, spaces), or null. */
    fun normalize(raw: String?): String? {
        val cleaned = clean(raw) ?: return null
        return cleaned.takeIf { isValid10(it) || isValid13(it) }
    }

    fun toIsbn13(isbn: String?): String? {
        val value = normalize(isbn) ?: return null
        if (value.length == 13) return value
        val body = "978" + value.take(9)
        return body + checkDigit13(body)
    }

    /** Only 978-prefixed ISBN-13s have an ISBN-10 twin. */
    fun toIsbn10(isbn: String?): String? {
        val value = normalize(isbn) ?: return null
        if (value.length == 10) return value
        if (!value.startsWith("978")) return null
        val body = value.substring(3, 12)
        val sum = body.mapIndexed { i, c -> (c - '0') * (10 - i) }.sum()
        val check = (11 - sum % 11) % 11
        return body + if (check == 10) "X" else check.toString()
    }

    private val QUERY = Regex("""^\s*(?:isbn)?\s*:?\s*([0-9Xx\- ]+)$""", RegexOption.IGNORE_CASE)

    /** A search query that is an ISBN (and nothing else): returns it normalized, else null. `1984` is a title. */
    fun fromQuery(query: String): String? = normalize(QUERY.find(query)?.groupValues?.get(1))

    private fun checkDigit13(first12: String): Int {
        val sum = first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10
    }
}
