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
   Ce fichier est gitignored.
3. Lancer sur un émulateur ou un appareil (minSdk 26 / Android 8+).

## Architecture (MVVM, comme l'app iOS)

- `model/` — `Game` (réponse IGDB), jamais persisté tel quel.
- `data/local/` — `GameEntity` (ligne Room persistée), `GameDao`,
  `AppDatabase`. Les collections que Room ne stocke pas nativement
  (genres, plateformes) sont sérialisées en JSON, comme le "JSON caching"
  de l'app iOS sur `GameEntity`.
- `data/remote/` — `IgdbApi` (requêtes Apicalypse en POST, jamais du
  REST classique), `TwitchAuthApi` + `IgdbTokenProvider` (jeton
  client-credentials mis en cache mémoire), `IgdbAuthInterceptor`.
- `data/repository/` — `IgdbService` (réserve le slot de rate-limit avant
  chaque appel, échappe les guillemets Apicalypse), `BacklogRepository`
  (unique source de vérité entre Room et IGDB — équivalent du rôle de
  `BacklogViewModel`/`GameSyncApplier` sur iOS).
- `ui/` — écrans Compose + ViewModels (`BacklogViewModel`), un package par
  écran (`ui/backlog/`, à étendre avec `ui/gamedetail/`, `ui/settings/`…).
- `util/` — `RateLimiter` (fenêtre glissante, 4 req/s sur IGDB, slot réservé
  avant l'appel — même règle que l'app iOS), `IgdbImage` (seul constructeur
  d'URL d'images IGDB, jamais construit à la main ailleurs).
- `config/Secrets.kt` — clés API, gitignored.

## Ce qui manque encore (prochaines étapes suggérées)

- Écran de détail d'un jeu (`ui/gamedetail/`)
- Notifications de sortie (WorkManager + AlarmManager, équivalent des
  notifications iOS)
- Notes Metacritic / Steam (nouveau service réseau, même schéma que
  `IgdbService`)
- Widget Home Screen (Glance, équivalent App Group/WidgetKit)
- Tests (Room in-memory pour les DAO, tests de ViewModel avec un
  repository fake)
- Localisation FR/EN (`strings.xml` par langue, comme les catalogues
  `.xcstrings` de l'app iOS)
