package com.davidgcd.backlog.util

/**
 * Open Library's "subjects" are free-form English tags mixed with catalogue noise ("Accessible book",
 * "Protected DAISY", "nyt:…"). This keeps the real genres and writes the common ones in French; an
 * unusual subject with no translation is kept as it is rather than dropped.
 */
object BookSubjects {
    private val NOISE = listOf(
        "accessible book", "protected daisy", "in library", "large type", "nyt:", "reading level", "open library",
        "overdrive", "internet archive", "lending library", "new york times", "cantook", "bestseller", "best seller",
        "fiction, general", "general",
    )

    private val FRENCH = mapOf(
        "science fiction" to "Science-fiction", "fiction" to "Fiction", "fantasy" to "Fantasy", "fantasy fiction" to "Fantasy",
        "juvenile fiction" to "Jeunesse", "juvenile literature" to "Jeunesse", "children's stories" to "Contes pour enfants",
        "young adult fiction" to "Jeunes adultes", "mystery" to "Policier", "detective and mystery stories" to "Policier",
        "mystery fiction" to "Policier", "crime" to "Policier", "romance" to "Romance", "love stories" to "Histoires d'amour",
        "history" to "Histoire", "historical fiction" to "Roman historique", "biography" to "Biographie",
        "autobiography" to "Autobiographie", "philosophy" to "Philosophie", "poetry" to "Poésie", "horror" to "Horreur",
        "horror fiction" to "Horreur", "adventure" to "Aventure", "adventure stories" to "Aventure", "classics" to "Classiques",
        "classic literature" to "Classiques", "graphic novels" to "Bande dessinée", "comics & graphic novels" to "Bande dessinée",
        "comic books, strips, etc." to "Bande dessinée", "thrillers" to "Thriller", "suspense" to "Thriller",
        "short stories" to "Nouvelles", "drama" to "Théâtre", "humor" to "Humour", "war" to "Guerre",
        "politics and government" to "Politique", "psychology" to "Psychologie", "religion" to "Religion", "travel" to "Voyages",
        "cooking" to "Cuisine", "science" to "Sciences", "fairy tales" to "Contes de fées", "dystopias" to "Dystopies",
        "dystopia" to "Dystopies", "magic" to "Magie", "wizards" to "Sorciers", "deserts" to "Déserts", "ecology" to "Écologie",
        "space opera" to "Space opera", "american fiction" to "Littérature américaine", "english fiction" to "Littérature anglaise",
        "french fiction" to "Littérature française", "literature" to "Littérature", "novels" to "Romans", "essays" to "Essais",
        "self-help" to "Développement personnel", "business" to "Économie", "economics" to "Économie", "art" to "Art",
        "music" to "Musique", "nature" to "Nature", "education" to "Éducation", "family" to "Famille", "friendship" to "Amitié",
        "politics" to "Politique", "social science" to "Sciences sociales", "technology" to "Technologie",
    )

    /** Real genres only, in French where known, no duplicates, at most [limit]. */
    fun clean(subjects: List<String>, limit: Int = 12): List<String> =
        subjects.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 40 }
            .filter { s -> NOISE.none { s.lowercase().contains(it) } }
            .map { FRENCH[it.lowercase()] ?: it }
            .distinctBy { it.lowercase() }
            .take(limit)
            .toList()
}
