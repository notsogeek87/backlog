# API — Livres

**Pour qui / pourquoi** : référence des services, modèles et formats du module Livres. Voir l'[ADR](../architecture/2026-10-01-livres-open-library.md).

| Élément | Rôle |
|---|---|
| `OpenLibraryService.searchBooks(query, page)` | titre, auteur ou ISBN (`isbn:` auto si la saisie est un ISBN valide) |
| `OpenLibraryService.getBook(workId, editionId, isbn)` | édition (ISBN, éditeur, pages, langue) + œuvre (description, sujets) |
| `OpenLibraryService.getEditions(workId)` | autres éditions d'une œuvre |
| `OpenLibraryService.getBookCover(book, 'S'/'M'/'L')` | URL de couverture (`BookImage`) |
| `GoogleBooksService.searchBooks` | secours, sans clé |
| `BookRepository` | `search`, `fetchRemote`, `editions`, `add` → `Added`/`Duplicate`, `setStatus`, `setFavorite`, `setUserRating`, `remove`, `enrich` |

Modèles : `Book` (transitoire), `BookEntity` (table `books`), `ReadStatus`, `BookKey`, `BookDuplicates`, `BookRanking`, `Isbn`.

## CSV de la bibliothèque (jeux + livres)

Un seul fichier pour tout : export manuel, import et export automatique (Réglages → Données).
Une colonne `type` (`GAME` / `BOOK`) distingue les lignes ; **sans colonne `type` (anciens exports de jeux) toutes les lignes sont des jeux**.
En-tête : `type`, puis les colonnes jeux **inchangées** (`name,igdbId,releaseDate,genres,platforms,archived,steamAppId,status,rank`),
puis les colonnes livres :
`subtitle,authors,isbn13,isbn10,publisher,publishedYear,pageCount,languages,description,coverUrl,workId,editionId,source,favorite,rating,addedAt`.

Un livre réutilise `name` (titre), `genres` (sujets) et `status` (`TO_READ`, `READING`, `READ`, `ABANDONED`) ; listes séparées par `;`.

```csv
type,name,igdbId,...,status,...,authors,isbn13,...,favorite
GAME,Hades,113112,...,PLAYED,...,,,...,
BOOK,Dune,,...,TO_READ,...,Frank Herbert,9782070368228,...,true
```

L'import complète chaque livre depuis Open Library (couverture, description, éditeur, pages, ids : par ISBN, sinon titre + auteur ; Google Books en secours), en best effort : hors ligne ou sans correspondance, la ligne est importée telle quelle. Statut, favori, note et date de la ligne sont toujours conservés. Les doublons sont ignorés (comptés dans « ignorés »).
