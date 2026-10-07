# Architecture de l'app (MVVM)

**Pour qui / pourquoi** : contributeurs qui doivent savoir dans quel package vit chaque responsabilité (données, repository, UI, notifications, partage). Extrait de l'ancien README.


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
- `ui/nav/` — `BacklogNavHost`, le seul `NavHost` de l'app : quatre onglets (Accueil, Bibliothèque, Découvrir, Réglages), détails, classement, « Mon année ». `AppAction` porte ce qu'un raccourci, le widget ou un texte partagé demande à l'ouverture. Voir [navigation et gestes](../guides/navigation-et-gestes.md).
- `ui/home/`, `ui/recap/` — accueil « Aujourd'hui » (`HomeViewModel` agrège les trois médias) et « Mon année » ; logique pure dans `model/Tonight`, `model/YearRecap`, `model/StatusFlow`.
- `ui/prefs/` — `UiPreferences` (SharedPreferences, lues avant la première frame) : thème, couleurs dynamiques, aperçu des bandes-annonces, drapeaux « déjà demandé ».
- `widget/` — widget Glance « en cours / prochaine sortie ».
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
  `PopularGamesLoader` sur iOS (cache mémoire stale-while-revalidate `StaleCache`, 10 min ;
  pas de cache disque) ; les jeux déjà dans le backlog sont filtrés
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
  `ui/ranking/` (jeux seulement : glisser-déposer + tout en haut / tout en bas + « placer à la position N », ouvert depuis le menu « ⋮ » de la Bibliothèque), tri « Mon classement »,
  colonne CSV `rank`. `model/Ranking` est le seul endroit qui ordonne (classés d'abord, puis non
  classés par date d'ajout, archivés exclus). Le partage en tient compte : liste numérotée « Mon
  classement » en tête, sur la page publique comme dans le texte de repli ; les autres jeux restent
  groupés par statut. La synchro IGDB conserve le rang (`refreshAndDetectDrift`).
- Lien public du backlog : `data/share/ShareLinkService` envoie un instantané (nom, statut,
  jaquette, lien igdb.com) au petit serveur `server/` (Vercel + Neon Francfort, voir
  `server/schema.sql`) et reçoit `https://…/b/<id>` ; le jeton secret reste sur l'appareil,
  les partages suivants mettent à jour le même lien. Serveur injoignable → repli sur le texte ci-dessous.
- Partage « ma librairie » : le bouton Partager des onglets Jeux et Films & séries ouvre un choix
  (`ui/components/ShareScopeDialog`) — partager la librairie ou uniquement l'onglet en cours. La librairie
  (`data/share/LibrarySharer` → `ShareLinkService.publishLibrary`, payload `kind: "library"`) donne un
  seul lien (supprimé 24 h après sa création, sans renouvellement ; un partage ultérieur en crée un nouveau) vers une page avec un onglet par typologie (Jeux, Films & séries ; onglets CSS sans script,
  un onglet vide n'est pas affiché), avec son propre lien/jeton. Serveur injoignable → texte de l'onglet en cours.
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

