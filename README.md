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
- `ui/discover/` — jeux IGDB en 5 listes sélectionnables par pastilles
  (`DiscoverCategory` : Populaires = `total_rating_count`, Mieux notés =
  `total_rating`, Tendances = sorties des ~18 derniers mois les plus notées,
  Nouveautés = sorties des ~3 derniers mois, À venir = `hypes`), équivalent simplifié de Découvertes/
  `PopularGamesLoader` sur iOS (pas de cache disque ni de stale-while-
  revalidate pour l'instant) ; les jeux déjà dans le backlog sont filtrés
  côté client.
- `data/csv/` — `CsvColumn` (en-têtes machine, `yyyy-MM-dd`, jamais
  traduits — même règle que `CSVColumn` sur iOS), `CsvFormat` (lecture/
  écriture RFC 4180 minimale), `CsvExportService`, `CsvImportService`
  (une ligne avec `igdbId` résout directement, une ligne nom-seul passe par
  une recherche classée avec `TitleSimilarity`, une ligne non résolue est
  ignorée sans jamais annuler tout l'import). Export automatique en plus de
  l'export manuel : à l'activation (Réglages › Données) l'utilisateur choisit un
  dossier via le sélecteur système (`OpenDocumentTree`, permission lecture+écriture
  persistée) et une fréquence (`AutoExportFrequency` : jour/semaine/mois) ;
  `AutoExportWorker` (WorkManager périodique) réécrit `backlog.csv` dans ce dossier
  et `AutoExportPreferences` mémorise le résultat du dernier passage (affiché dans
  Réglages, en erreur si le dossier n'est plus accessible). Désactiver libère la permission.
- Classement personnel (`GameEntity.userRank`, migration 4→5, 1 = le plus aimé) : écran
  `ui/ranking/` (flèches monter/descendre, ouvert depuis la barre du Backlog), tri « Mon classement »,
  colonne CSV `rank`. `model/Ranking` est le seul endroit qui ordonne (classés d'abord, puis non
  classés par date d'ajout, archivés exclus). Le partage en tient compte : liste numérotée « Mon
  classement » en tête, sur la page publique comme dans le texte de repli ; les autres jeux restent
  groupés par statut. La synchro IGDB conserve le rang (`refreshAndDetectDrift`).
- Lien public du backlog : `data/share/ShareLinkService` envoie un instantané (nom, statut,
  jaquette, lien igdb.com) au petit serveur `server/` (Vercel + Neon Francfort, voir
  `server/schema.sql`) et reçoit `https://…/b/<id>` ; le jeton secret reste sur l'appareil,
  les partages suivants mettent à jour le même lien. Serveur injoignable → repli sur le texte ci-dessous.
- Partage du backlog : bouton Partager dans la barre du Backlog → feuille de partage
  Android (`Intent.ACTION_SEND`, `text/plain`) avec la liste des jeux non archivés (+ lien igdb.com de chaque jeu via `IgdbService.getGameUrls`, sans lien si IGDB est injoignable)
  groupés par statut, texte construit par `util/BacklogShareText` (indépendant du
  filtre/tri en cours). Pas de compte ni de lien en ligne : l'app reste 100 % locale.
- Statut d'un jeu (`model/GameStatus`, colonne `status` de `games`, migration 3→4) :
  `BACKLOG` (défaut), `PLAYED` (joué), `COMPLETED`
  (terminé). Modifiable sur la fiche du jeu, filtrable dans le backlog, et
  exporté/importé dans la colonne CSV `status`.
- Grands écrans (écran interne d'un pliant type Galaxy Z Fold, tablette) :
  l'activité gère elle-même les changements de taille (`configChanges`,
  pas de recréation au pli/dépli), les grilles Backlog/Découvrir sont
  adaptatives (colonnes de 340 dp min) et la fiche d'un jeu passe sur deux
  colonnes dès 600 dp de large (cover + actions à gauche, statut/notes/
  résumé à droite).
- `notifications/` — `NotificationPreferences` (DataStore, équivalent
  `UserDefaults`/`NotificationPolicyStore` — délai configurable via
  `ReleaseReminderSchedule`, heure, et les deux alertes de dérive),
  `ReleaseReminderWorker` + `ReleaseReminderScheduler` (WorkManager
  périodique quotidien réancré à l'heure choisie, équivalent simplifié du
  `NightlySyncService`/`BGTaskScheduler` iOS — trois choses vérifiées à
  chaque passage : le rappel de sortie au délai choisi, un changement de
  date, une nouvelle plateforme ; `BacklogRepository.refreshAndDetectDrift`
  fait le travail de données, le worker celui de la notification, même
  séparation que `SyncDriftDispatcher` côté iOS), `NotificationIds`
  (fabrique d'identifiants, jamais construits à la main ailleurs — même
  règle que `NotificationIdentifier` côté iOS).
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

## Import de bibliothèque Steam

Réglages → *Mes plateformes*. Voir [ADR](docs/architecture/2026-09-29-library-providers.md),
[API providers](docs/api/library-providers.md) et [guide](docs/guides/steam-import.md).

Films & séries (onglet dédié, données IMDb, connexion au compte IMDb pour importer watchlist et
notes) : voir [ADR](docs/architecture/2026-09-30-films-series-imdb.md) et
[guide](docs/guides/imdb-import.md).
Nécessite `STEAM_API_KEY` dans `Secrets.kt` (voir `Secrets.kt.example`).

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

## Localisation (français uniquement)

L'app est en français uniquement : `values/strings.xml` est en français et la
locale est forcée à `fr` (`MainActivity`, `BacklogApplication`). Les noms de genres/
plateformes IGDB, le nom des jeux et le verdict Steam ("Overwhelmingly
Positive"…) restent tels que l'API les renvoie, jamais traduits — même
règle que l'app iOS sur le contenu API. `BacklogSort`/`ReleaseReminderSchedule`
stockent un nom d'enum stable ; leur libellé affiché est résolu à l'écran
via `stringResource`, jamais stocké traduit. Limite connue : les messages
d'erreur de recherche (`BacklogViewModel.searchError`) et les noms de jeu
non résolus lors d'un import CSV restent en anglais pour l'instant — les
traduire demanderait de faire circuler un `Context`/`Resources` jusque
dans les ViewModels, non fait dans ce premier passage.

## Tests

`app/src/test/` — tourne dans `testDebugUnitTest` (celui que la CI exécute),
sans émulateur :
- `TitleSimilarityTest`, `ReleaseDateFormattingTest` — logique pure.
- `GameDaoTest` — Room en mémoire **sous Robolectric**, pour avoir un vrai
  test de DAO sans matériel connecté.
- `BacklogViewModelTest` — `FakeGameDao`/`FakeIgdbApi` (interfaces, donc
  fakables sans mock ni Room), couvre le tri, le filtre archivés/genre et
  la distinction "backlog vide" vs "résultats filtrés vides".

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
