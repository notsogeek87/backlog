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

## CSV des livres (Réglages → Données)

En-têtes machine, listes séparées par `;`, une ligne par livre. Le CSV des jeux est inchangé.

```csv
type,title,authors,isbn13,status,favorite
BOOK,Dune,Frank Herbert,9782070368228,TO_READ,false
```

Colonnes : `type,title,subtitle,authors,isbn13,isbn10,publisher,publishedYear,pageCount,languages,subjects,description,coverUrl,workId,editionId,source,status,favorite,rating,addedAt`.
Statuts : `TO_READ`, `READING`, `READ`, `ABANDONED`. Seul `title` est obligatoire ; une ligne d'un autre `type` est ignorée.
L'import ne contacte aucun catalogue et saute les doublons.
