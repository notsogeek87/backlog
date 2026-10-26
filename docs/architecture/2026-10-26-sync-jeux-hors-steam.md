# Piste — synchroniser les jeux hors Steam (Switch, IGDB…)

**Pour qui / pourquoi** : reprendre plus tard la réflexion « connexion IGDB / compte qui synchronise les jeux Switch » sans refaire l'enquête. Aucun code n'a été écrit : c'est un état des lieux et une décision en attente.

## Besoin exprimé
- Ajouter une « connexion IGDB » qui se synchronise comme Steam.
- Vrai blocage : pouvoir gérer des jeux **Switch** (pas de store synchronisable), dans **un compte à soi** qui se synchronise.

## Constats (vérifiés le 2026-10-26)
- **IGDB** : catalogue seulement. Aucune API de lecture des listes d'un compte (Played, Playing, Backlog…). L'app l'utilise déjà (`IgdbService`) via des identifiants Twitch propres à l'app.
- **Ajout manuel** : la recherche IGDB n'a pas de filtre de plateforme, un jeu Switch s'ajoute déjà à la main. Il n'y a pas de champ « je joue dessus sur : Switch » par jeu (une fiche = un jeu IGDB).
- **Stockage** : tout est local (Room), aucun compte ni backend. Un jeu ajouté n'existe que sur le téléphone ; rien n'est écrit vers IGDB ni Nintendo. Seule exception : TMDB (notes et watchlist des films). Désinstaller l'app = perdre la liste, sauf export CSV ou sauvegarde Android.
- **Nintendo** : aucune API publique de bibliothèque.
- **Approche « comme IMDb » (scraping)** : écartée, voir [Films et séries via TMDB](2026-09-30-films-series-tmdb.md) (fragile, cookies rejoués).

## Sites de suivi testés
| Site | Résultat |
|---|---|
| Backloggd | Le plus connu, mais sans API et protégé par un contrôle anti-bot : pas de scraping. Export CSV annoncé « prévu » sur sa roadmap. |
| RAWG | API officielle, mais 30 endpoints de catalogue uniquement, aucune liste de compte. |
| Keep Track of My Games, BrotherMo VGC | Export / import CSV, Switch inclus. Fonctionnent par fichier. |
| PriceCharting | Export CSV zippé par e-mail (1 par semaine en gratuit). |
| Infinite Backlog | Import par pseudo public, PlayStation et Steam seulement. |
| Gamevaro | Seule API REST trouvée, sur offre payante. |

Conclusion : aucun site ne lit une bibliothèque Switch ; tous reposent sur une saisie manuelle.

## Options en attente de décision
1. **Import CSV** depuis un site tiers (Keep Track of My Games ou BrotherMo VGC), avec l'aperçu et l'import de `LibrarySyncService` (nouveau `GameLibraryProvider`). Rapide, mais un outil de plus à tenir à jour.
2. **Compte Backlog sur le serveur Neon existant** (recommandé pour un « compte à soi ») : connexion dans l'app, jeux stockés en base, synchro entre appareils et restauration. Question ouverte : e-mail + mot de passe ou connexion Google.
3. **Saisie en lot** de titres (collés ou tapés), rapprochés d'IGDB avec aperçu, plus un filtre de recherche « Switch » et éventuellement une plateforme par jeu.
4. **Rafraîchissement des métadonnées IGDB** de tout le backlog (n'importe pas de listes).
5. **Backloggd via WebView** : possible mais non testable (le site bloque les robots) et fragile ; non recommandé.

Voir aussi : [Providers de bibliothèque](../api/library-providers.md) · [ADR import de bibliothèques](2026-09-29-library-providers.md).
