<p align="center"><img src="docs/brand/logo-512.png" width="128" alt="Logo Backlog" /></p>

# Backlog (Android)

App Android de gestion de bibliothèque de jeux vidéo, inspirée de l'app iOS
[Backlog](https://github.com/loicgff/backlog) — même idée (suivre ce que tu
possèdes / veux jouer, dates de sortie IGDB, notes Metacritic/Steam), portée
sur Android avec des briques Android natives.

## Stack

- **Kotlin + Jetpack Compose** pour l'UI (équivalent Swift + SwiftUI)
- **Room** pour la persistance locale (équivalent SwiftData) — `GameEntity`
  est la ligne persistée, `Game` est le modèle brut de réponse IGDB, comme
  sur l'app iOS (`GameEntity` vs `Game`)
- **Retrofit + Moshi** pour IGDB (via l'API Twitch, client-credentials)
- **Coil** pour le chargement/cache d'images de jaquettes
- Pas de couche cloud/sync pour l'instant (choix du premier jet) : tout est
  local via Room. Un ajout futur type Firebase/backend perso pourra brancher
  une sync multi-appareils sans toucher à l'UI, si le repository reste la
  seule source de vérité (`BacklogRepository`).

## Démarrer

1. Ouvrir le dossier dans **Android Studio** (version récente, ex. Ladybug+)
   — il régénère automatiquement le wrapper Gradle (`gradlew`) au premier
   sync, pas besoin de l'installer à la main.
2. Copier `app/src/main/java/com/davidgcd/backlog/config/Secrets.kt.example`
   vers `Secrets.kt` (même dossier) et renseigner un client ID/secret Twitch
   Developer (IGDB tourne sur l'API Twitch : https://api-docs.igdb.com).
   Ce fichier est gitignored. Les notes Steam marchent sans rien remplir
   (API publique) ; les notes Metacritic restent absentes tant que
   `METACRITIC_RAPIDAPI_HOST`/`_KEY` ne pointent pas vers un vrai
   fournisseur RapidAPI (voir le commentaire dans `Secrets.kt.example`).
3. Lancer sur un émulateur ou un appareil (minSdk 26 / Android 8+).

## Architecture (MVVM, comme l'app iOS)

Couches `model/`, `data/` (local, remote, repository, csv, share), `ui/`, `notifications/`, `util/` : voir
[docs/architecture/overview.md](docs/architecture/overview.md). `BacklogRepository` reste la seule source de vérité
entre Room et les API distantes. Design system : [docs/architecture/design-system.md](docs/architecture/design-system.md).

## Import de bibliothèque Steam

Réglages → *Mes plateformes*. Voir [ADR](docs/architecture/2026-09-29-library-providers.md),
[API providers](docs/api/library-providers.md) et [guide](docs/guides/steam-import.md).

Films & séries (onglet dédié, API officielle TMDB, connexion au compte TMDB pour importer/écrire
watchlist et notes) : voir [ADR](docs/architecture/2026-09-30-films-series-tmdb.md) et
[guide](docs/guides/tmdb-import.md).
La **liste de souhaits Steam** se synchronise depuis la même carte (statut « Souhaité », sens unique Steam → app, voir le [guide](docs/guides/steam-import.md)).
Nécessite `STEAM_API_KEY` dans `Secrets.kt` (voir `Secrets.kt.example`).

## Livres

Onglet **Livres** : recherche par titre, auteur ou ISBN sur Open Library (Google Books en secours, aucune clé
requise), statuts À lire / En cours / Lu / Abandonné, favoris, éditions multiples, détection de doublons,
export/import CSV commun avec les jeux (export automatique inclus). Voir l'[ADR](docs/architecture/2026-10-01-livres-open-library.md),
l'[API](docs/api/books.md) et le [guide](docs/guides/books.md).

## Notes Metacritic : scaffold, pas branché

Codé mais non branché tant qu'aucun fournisseur RapidAPI n'est configuré (aucune erreur visible, la carte Steam s'affiche seule).
Voir [docs/api/metacritic.md](docs/api/metacritic.md).

## Partage par lien

Petit serveur Vercel + Neon dans `server/` : voir [docs/api/share-server.md](docs/api/share-server.md).

## Sauvegarde, localisation, tests

- [Sauvegarde et restauration (Android / Samsung)](docs/guides/backup-restore.md)
- [Localisation (français uniquement)](docs/guides/localisation.md)
- [Tests](docs/guides/testing.md)

Toute la documentation est indexée dans [docs/README.md](docs/README.md) ; pour contribuer, voir [CONTRIBUTING.md](CONTRIBUTING.md).

## Ce qui manque encore (prochaines étapes suggérées)

- Un vrai fournisseur Metacritic branché sur `MetacriticApi` (voir plus haut)
- Widget Home Screen (Glance, équivalent App Group/WidgetKit)
- Sync cloud multi-appareils (Firebase ou backend perso)
- Traduire les messages d'erreur/résultats qui restent en anglais (voir
  "Localisation FR/EN" ci-dessus)
- Cache disque + stale-while-revalidate pour Découvertes (actuellement un
  fetch à chaque ouverture de l'écran)
- Tests instrumentés (androidTest) pour la navigation et les permissions,
  au-delà de ce que Robolectric peut couvrir en JVM
