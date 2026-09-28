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

- `model/` — `Game` (réponse IGDB), jamais persisté tel quel.
- `data/local/` — `GameEntity` (ligne Room persistée), `GameDao`,
  `AppDatabase`, `GameJsonCache` (décode `genresJson`/`platformsJson` —
  le côté lecture du "JSON caching" que l'app iOS applique aussi à son
  `GameEntity`; les filtres du backlog et l'écran de détail passent par lui,
  jamais par un décodage ad hoc).
- `data/remote/` — `IgdbApi` (requêtes Apicalypse en POST, jamais du
  REST classique), `TwitchAuthApi` + `IgdbTokenProvider` (jeton
  client-credentials mis en cache mémoire), `IgdbAuthInterceptor`,
  `SteamApi` (endpoint public `appreviews`, `purchase_type=all` forcé —
  voir `docs Steam` dans `SteamApi.kt`), `MetacriticApi` (scaffold, voir
  plus bas).
- `data/repository/` — `IgdbService` (réserve le slot de rate-limit avant
  chaque appel, échappe les guillemets Apicalypse), `BacklogRepository`
  (unique source de vérité entre Room et IGDB — équivalent du rôle de
  `BacklogViewModel`/`GameSyncApplier` sur iOS), `SteamService` (notes
  Steam, gratuit, ne lève jamais — pas de note = pas de carte, jamais de
  message d'erreur), `MetacriticService` (même règle "pas d'erreur
  visible", mais **scaffold non branché** : voir "Ce qui manque encore").
- `ui/nav/` — `BacklogNavHost`, le seul `NavHost` de l'app (Backlog → détail
  d'un jeu, Backlog → Réglages).
- `ui/` — écrans Compose + ViewModels, un package par écran : `ui/backlog/`
  (liste + recherche IGDB + tri/filtre — `BacklogSort`/`BacklogFilter`,
  genre/plateforme dérivés des `GameEntity` du backlog, jamais figés en dur),
  `ui/gamedetail/` (un jeu du backlog **ou** un résultat de recherche pas
  encore ajouté — `GameDetailState` distingue les deux, comme
  `HomeRoute.game(id:)` sur iOS ; section Notes = `RatingsState`, jamais de
  "note indisponible" affiché, comme la section Notes de l'app iOS),
  `ui/settings/` (réglages de notifications).
- `notifications/` — `NotificationPreferences` (DataStore, équivalent
  `UserDefaults`/`NotificationPolicyStore`), `ReleaseReminderWorker` +
  `ReleaseReminderScheduler` (WorkManager périodique quotidien, équivalent
  simplifié du `NightlySyncService`/`BGTaskScheduler` iOS — un seul cas géré
  pour l'instant : "sort aujourd'hui", pas encore de choix d'heure/délai ni
  d'alertes de changement de date/plateforme), `NotificationIds` (fabrique
  d'identifiants, jamais construits à la main ailleurs — même règle que
  `NotificationIdentifier` côté iOS).
- `util/` — `RateLimiter` (fenêtre glissante, 4 req/s sur IGDB, slot réservé
  avant l'appel — même règle que l'app iOS), `IgdbImage` (seul constructeur
  d'URL d'images IGDB, jamais construit à la main ailleurs),
  `ReleaseDateFormatting` (seule conversion du timestamp IGDB — pas encore
  de gestion de précision de date comme `ReleaseDateCategory` côté iOS),
  `TitleSimilarity` (Levenshtein normalisé + seuil 0.8, utilisé par
  `MetacriticService` pour rapprocher un résultat de recherche du jeu —
  même rôle que `TitleSimilarity` sur iOS, à réutiliser si un futur
  HowLongToBeat arrive plutôt que d'en réécrire un second), `AppLogger`
  (seul point de logging — jamais de `Log.*` en direct ailleurs).
- `config/Secrets.kt` — clés API, gitignored.

## Notes Metacritic : scaffold, pas branché

Metacritic n'a pas d'API officielle gratuite (l'app iOS passe par un proxy
payant qu'elle a construit elle-même). `MetacriticApi`/`MetacriticService`
sont codés avec la même architecture que Steam/IGDB (interface Retrofit,
DTO, appel réseau, rapprochement de titre via `TitleSimilarity`), mais le
endpoint (`MetacriticApi.search`) et la forme de la réponse
(`MetacriticSearchResult`) sont un **gabarit** à ajuster une fois que tu as
un vrai abonnement RapidAPI — cherche "metacritic" sur rapidapi.com, mets
son host/clé dans `Secrets.kt`, et adapte l'interface au contrat exact de
ce fournisseur. Tant que ce n'est pas fait, la section Notes affiche
simplement la carte Steam seule (aucune erreur, aucun texte "indisponible" —
même règle que HowLongToBeat sur iOS).

Le lien Steam (`GameEntity.steamAppId`) est extrait automatiquement du
champ IGDB `websites` (catégorie 13) au premier chargement d'un jeu, et
persisté — pas besoin de le renseigner à la main.

## Ce qui manque encore (prochaines étapes suggérées)

- Un vrai fournisseur Metacritic branché sur `MetacriticApi` (voir ci-dessus)
- Choix du délai de notification (à la sortie / veille / semaine avant),
  alertes de changement de date ou de plateformes
- Widget Home Screen (Glance, équivalent App Group/WidgetKit)
- Tests (Room in-memory pour les DAO, tests de ViewModel avec un
  repository fake — `BacklogViewModelTest` sur le tri/filtre serait le
  premier candidat naturel)
- Localisation FR/EN (`strings.xml` par langue, comme les catalogues
  `.xcstrings` de l'app iOS)
- Une vraie migration Room quand l'app aura un premier utilisateur
  installé (`AppDatabase` utilise `fallbackToDestructiveMigration()` pour
  l'instant, acceptable seulement avant toute sortie)
