# Audit UX/UI — Backlog (Android)

**Pour qui / pourquoi** : contributeurs qui veulent savoir ce que l'audit d'octobre 2026 a relevé, ce qui a été traité et ce qui reste. Remplace l'audit de la première version (palette crème, 3 onglets), devenu caduc.

Périmètre : navigation, listes (jeux, films & séries, livres), recherche, filtres/tri, fiches détail, classement, Découvrir, Réglages, thème, accessibilité. Audit fait **sur le code** (pas de test sur appareil) ; les correctifs ont été compilés et testés en JVM (`testDebugUnitTest`, `lintDebug`) mais **pas essayés à la main sur un téléphone** : voir « Reste à valider ».

## Corrections (problèmes constatés)

| # | Sév. | Constat | Traitement |
|---|------|---------|------------|
| 1 | Haute | Aucune recherche dans sa propre liste : la loupe interrogeait seulement le catalogue en ligne. | Deux portées « Ma liste » (filtre instantané, accents et casse ignorés, `LibraryQuery`) et « Catalogue ». |
| 2 | Haute | « Ajouter » tout en bas d'une fiche, sous le résumé. | `StickyActionBar` : le bouton reste visible (jeux, films & séries, livres). |
| 3 | Haute | Texte blanc sur le dégradé cyan du bouton principal (≈ 1,8:1 à gauche). | Texte `Glass.OnAccent` (marine sur le dégradé sombre, blanc sur le dégradé clair), ≥ 4,5:1. |
| 4 | Haute | `clickable` sans rôle ni état : filtres et statuts non annoncés par TalkBack, jaquettes sans nom. | `Role.RadioButton` + `selected` sur pills/badges/tuiles, `Role.Button` sur boutons, `contentDescription` sur les jaquettes cliquables, barres de note décrites, gestes exposés en actions personnalisées. |
| 5 | Moy. | Cibles de 36 dp (pills), 4 flèches de 36 dp au classement. | `minimumInteractiveComponentSize()` (48 dp), lignes du classement refaites (44 dp + poignée). |
| 6 | Moy. | Permission de notifications demandée au lancement, jusqu'à 3 fenêtres d'affilée. | Demande contextuelle (carte sur l'accueil quand il y a des sorties, bannière dans Réglages) ; `WhatsNewPrompt` attend la fenêtre de mise à jour. |
| 7 | Moy. | Tuiles de stats inertes ; « Souhaité » à la fois statut et portée. | Tuiles = filtre de statut (état sélectionné) ; la portée Tous/Souhaités/Possédés passe dans la feuille de filtres. |
| 8 | Moy. | Filtre et tri en longs menus à radios, sans compteur, tri actif invisible. | `FilterSortSheet` (puces, bouton « Voir N résultats »), libellé « Tri : … » sous le titre de section. |
| 9 | Moy. | Barre du haut à 5 icônes. | Recherche + Filtrer/trier + menu « ⋮ » (partager, classement, import TMDB). |
| 10 | Moy. | Recherche, partage, état vide, chips recopiés 3 fois. | `LibraryParts`, `ShareFlow`, `FilterSheet`, `QuickActionsSheet`, `SwipeActions` partagés. |
| 11 | Moy. | Un jeu à venir était ajouté en « Backlog ». | `GameStatus.suggestedFor` : « Souhaité » si pas encore sorti ; appui long sur « + » pour choisir le statut. |
| 12 | Basse | Classement : état vide aligné à gauche, jeux seulement. | État vide centré avec action ; classement pour les trois médias. |
| 13 | Basse | Pseudo de partage enregistré à chaque frappe ; journal de debug visible. | Enregistrement après 600 ms de pause ; journal caché derrière 7 touchers sur la version. |
| 14 | Basse | Pas de note perso pour les jeux. | Note 1–10 (`GameEntity.userRating`), demandée au passage à « Terminé ». |
| 15 | Basse | Suppression sans annulation ; Découvrir rechargé à chaque ouverture ; spinner dans le vide. | Suppression sans dialogue + Snackbar « Annuler » (`AppSnackbar`) ; cache mémoire `StaleCache` (frais 10 min, affiché tout de suite, rafraîchi en arrière-plan) ; `SkeletonList`. |

## Propositions UX appliquées
Accueil « Aujourd'hui » (en cours, sorties, ajouts récents, bilan), « Ce soir, je fais quoi ? » (`model/Tonight`), navigation à 4 onglets (Accueil, Bibliothèque avec sélecteur de média, Découvrir, Réglages), balayage et appui long sur les lignes, statut en un toucher depuis la liste, classement par glisser-déposer **avec** « tout en haut / tout en bas » et « placer à la position N », fond des fiches teinté par la couleur dominante de la jaquette, bande-annonce en tête de fiche (aperçu muet optionnel), alertes promo Steam et nouveaux épisodes, widget et raccourcis d'icône, « Partager vers Backlog », « Mon année » partageable en image, thèmes clair / sombre / AMOLED / couleurs dynamiques. Détail : [guide navigation et gestes](../guides/navigation-et-gestes.md).

## Écarts assumés avec les propositions
- **« Ce soir »** : la durée des **jeux** n'est pas connue (l'app ne stocke pas le « time to beat » d'IGDB) ; ils passent donc tous les filtres de temps. Films (durée TMDB) et livres (pages ÷ 40 par heure) sont filtrés.
- **Mode « duel »** du classement : non fait (non demandé : seule la partie glisser-déposer + haut/bas l'était).
- **Gestes de balayage** : sur les lignes (jeux, films & séries). Les livres sont une grille de jaquettes : appui long et menu du badge de statut à la place.
- **Aperçu animé des bandes-annonces** : désactivé par défaut (consomme des données), à activer dans Réglages.
- **« Mon année »** : la date de fin n'est retenue que depuis cette version (`completedAt`) ; avant, un élément terminé est daté de son ajout.

## Reste à valider
- Tout passer sur un appareil (glisser-déposer avec défilement automatique, balayages dans la grille adaptative, widget, raccourcis, partage d'image, thème clair).
- Accessibility Scanner et lecture TalkBack complète.
- Les alertes promo / épisodes n'ont pas été éprouvées contre les API réelles (seuls les analyseurs et les règles sont testés).
- Contraste du thème clair et du mode « couleurs dynamiques » à mesurer.
