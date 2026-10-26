# Guide — Réviser mes notes

**Pour qui / pourquoi** : utilisateurs et développeurs qui veulent repasser toute leur bibliothèque (jeux, films & séries, livres) pour remettre leurs notes à jour.

## Utilisation
Accueil → carte **Réviser mes notes**. L'écran présente une fiche à la fois :

- la note actuelle et sa date (« mise le 12 mars 2025 », « date inconnue » pour les notes d'avant cette fonction, « pas encore noté ») ;
- un toucher sur une note l'enregistre et passe à la fiche suivante (1 à 10 pour les jeux et les films & séries, 1 à 5 étoiles pour les livres) ;
- **Garder X/10** confirme la note telle quelle : elle prend la date du jour ;
- **Passer** laisse la fiche intacte.

À la fin, **Recommencer** reconstruit la file. Les jeux et films archivés sont ignorés.

## Ordre de passage
Des notes les plus **anciennes** aux plus **récentes**. Ce qui n'a jamais été daté (notes d'avant la fonction, éléments pas encore notés) passe d'abord, **au hasard** ; l'aléatoire sert aussi à départager deux dates égales. La file est figée au lancement : re-noter une fiche ne réordonne rien.

## Côté code
- `ratedAt` (UTC, ms) sur `GameEntity`, `MovieEntity` et `BookEntity`, ajouté par la migration 10 → 11. Il est écrit par les `setUserRating` des trois dépôts (et par la synchro TMDB quand la note TMDB change) ; effacer une note l'efface.
- `model/RatingReview.kt` construit la file (fonction pure, testée dans `RatingReviewTest`).
- `ui/review/` : `RatingReviewViewModel` (file figée, position, compteur) et `RatingReviewScreen`.
- Route `review`, entrée dans le journal des nouveautés (`Changelog`, id 17).
