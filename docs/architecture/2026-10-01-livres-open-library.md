# ADR — Livres (Open Library, Google Books en secours)

**Pour qui / pourquoi** : toute personne qui touche au module Livres. Il explique pourquoi les livres
sont un troisième type de contenu à côté des jeux (IGDB/Steam) et des films & séries (TMDB), sans
toucher à `Game`/`GameEntity`.

## Décisions

- **Une table à part**, `books` (migration 8→9), comme `movies` : `BookEntity` / `BookDao`. Rien ne change dans `games` ni `movies`.
- **Chaîne catalogue → ligne locale → UI** : un livre ajouté est stocké en entier (description, couverture, ISBN,
  édition…). Les écrans lisent Room, jamais le réseau ; la bibliothèque s'affiche hors ligne.
- **Catalogues** : `OpenLibraryService` (principal, sans clé) puis `GoogleBooksService` (secours, API publique
  **sans clé** — aucun secret ajouté à `Secrets.kt`). Google n'est interrogé que si Open Library est en panne ou ne trouve rien.
  Toute l'app passe par `BookRepository` ; aucun composant n'appelle une API.
- **Œuvre / édition** : un résultat de recherche = une *œuvre* (`workId`) ; `editionId` et ISBN identifient l'édition.
  Chaque édition ajoutée est sa propre ligne ; la fiche liste les autres éditions (`/works/{id}/editions.json`).
- **Clé d'un livre** (`BookKey`, fixée à la création, jamais modifiée) : `isbn:<ISBN-13>` (un ISBN-10 est converti) → `ol:<édition>` → `work:<œuvre>` → `gb:<volume>` → hash titre+auteur.
- **Doublons** (`BookDuplicates`) : ISBN-13, ISBN-10, id d'édition Open Library, puis titre+auteur — ce dernier
  seulement si l'un des deux n'a pas d'ISBN : deux ISBN différents = deux éditions, pas un doublon.
- **Statuts** `ReadStatus` : `TO_READ`, `READING`, `READ`, `ABANDONED` ; « favori » est un booléen indépendant ; note perso 1–5.
- **Classement des résultats** (`BookRanking`) : titre exact, puis préfixe, puis couverture, puis édition française ; l'ordre du catalogue départage. `lang=fr` est envoyé à Open Library.
- **Performance** : debounce 300 ms, pages de 20 (« Voir plus »), cache mémoire des recherches (30 entrées, 10 min), Coil pour les images.

## Français

- **Genres** : `BookSubjects` retire le bruit du catalogue (« Accessible book », « nyt:… »…) et traduit les genres courants (Science-fiction, Policier…). Un sujet inhabituel sans traduction est conservé tel quel.
- **Description** : si celle d'Open Library n'est pas en français (heuristique `BookText.looksFrench`), le dépôt cherche une description française sur Google Books (`langRestrict=fr`, par ISBN puis titre + auteur). Sans résultat, l'originale est conservée. Un échec réseau n'est pas mémorisé (nouvel essai à la prochaine ouverture).
- Langue de l'édition : affichée par son nom français (« Anglais », « Français »).

## Limites connues

- Open Library : un résultat de recherche ne dit pas quel ISBN/langue correspond à *quelle* édition ; ils sont lus
  ensuite (édition de couverture) en tâche de fond après l'ajout. Hors ligne au moment de l'ajout, ils sont complétés à la prochaine ouverture de la fiche.
- Les descriptions et sujets Open Library sont parfois absents ou en anglais ; les champs vides ne sont pas affichés.
- Google Books sans clé a un quota anonyme limité.
- Les livres ne sont pas (encore) dans le partage public (`server/`) ni dans l'onglet Découvrir.
