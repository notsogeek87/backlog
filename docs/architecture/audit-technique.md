# Audit technique — Backlog (octobre 2026)

**Pour qui / pourquoi** : mainteneur et contributeurs qui veulent savoir où en est l'app côté sécurité, architecture, build et serveur, et quoi faire ensuite, par ordre de priorité. Complète l'[audit UX/UI](ux-audit.md), qui couvre déjà l'interface et l'accessibilité (non repris ici).

Audit fait **sur le code** de `main` au 31/10/2026 (commit `e11b4ce`) : lecture du code Android, du serveur `server/`, de la CI et de la doc. Tests serveur lancés (`npm test` : 22/22 OK), liens de doc vérifiés (`tools/check_docs_links.py` : OK). Le build Android n'a pas été relancé pour cet audit (pas de SDK Android dans l'environnement de l'audit) : la CI reste la référence.

## Suivi des corrections (31/10/2026)

Décision : les liens de partage **restent valables 24 h** (pas d'option de lien durable).

| # | État | Ce qui a été fait |
|---|------|-------------------|
| S1 | Fait (code) | `server/api/igdb-token.js` + `lib/igdbToken.js` : le serveur détient le secret et sert le jeton (partagé via la table `app_tokens`). L'app (`IgdbTokenApi`, `IgdbTokenProvider`) ne contient plus aucun identifiant Twitch ; `Secrets.kt.example` et la CI non plus. **Reste à faire par le mainteneur** : renseigner `TWITCH_CLIENT_ID` / `TWITCH_CLIENT_SECRET` dans Vercel, puis régénérer le secret sur dev.twitch.tv (l'ancien est sorti dans les APK publiés). |
| S2 | Partiel | Steam et TMDB inchangés (clés dédiées et révocables, documentées). Le proxy Steam reste possible via `STEAM_API_BASE_URL`. |
| S3 | Fait | `lib/rateLimit.js` (20 créations/h, 30 jetons/h par client), plafond de 5 000 liens vivants, corps limité à 2 Mo. Tables créées sur Neon. |
| S4 | Fait | `ValidationError` : seul un défaut de la charge utile renvoie son message. |
| S5 | Fait | `CRON_SECRET` obligatoire pour `/api/purge` (à vérifier dans Vercel, sinon le cron reçoit 401 ; la purge tourne aussi à chaque création de lien). |
| A1 | Fait | Un seul `OkHttpClient` de base, dérivé par `newBuilder()` (pool partagé). Pas de cache disque : les réponses de compte TMDB ne doivent pas être servies périmées. |
| A2 | Fait | `AppContainer` (services `lazy`) ; `BacklogApplication` réduit à l'exposition des services. |
| A3 | Fait | Les quatre plus gros écrans sont scindés en `…Screen.kt` (l'écran) et `…Parts.kt` / `SettingsRows.kt` (grilles, lignes, dialogues). |
| A4 | Fait | `model/PersonalRanking` porte les règles communes ; `Ranking`, `MovieRanking`, `BookTop` y délèguent. |
| A5 | Fait | `IgdbAuthenticator` : sur 401, jeton invalidé, signalé au serveur, requête rejouée une fois. |
| A6 | Fait | `exportSchema = true` (schémas dans `app/schemas/`) et `MigrationChainTest` : une base version 1 migrée jusqu'à la version actuelle, schéma validé par Room, données conservées. |
| B1 | Fait | R8 actif pour `release` et `github`, en mode prudent : le code de l'app est gardé tel quel, seules les bibliothèques sont réduites (`proguard-rules.pro`). **À essayer sur un téléphone** avant de fusionner sur `main`. |
| B2–B4 | Reporté | Moshi codegen, montée de versions groupée et Baseline Profile : chacun mérite sa propre branche testée sur appareil. |
| C1 | Fait | Workflow `server-tests.yml`. |
| C2 | Fait | Chemins `../scripts/…` dans `server/package.json`. |
| C3–C4 | Reporté | Nettoyage des pré-releases et couverture : confort, sans risque. |

## Points forts

- **Une vraie discipline de migrations Room** : 11 versions, une migration par saut, plus jamais de `fallbackToDestructiveMigration` (`data/local/Migrations.kt`).
- **Sauvegarde bien pensée** : règles `backup_rules.xml` / `data_extraction_rules.xml` en liste blanche ; la session TMDB est dans `noBackupFilesDir`, donc jamais copiée dans une sauvegarde.
- **Serveur de partage sobre et défensif** : tout le contenu reçu est revalidé (`validatePayload`), échappement HTML systématique, CSP sans script, jeton d'édition stocké haché et comparé en temps constant, liens qui expirent après 24 h.
- **Signature et publication** : clé de publication distincte de la clé Play, refus de publier sur `main` sans elle, `versionCode` strictement croissant pour les mises à jour intégrées.
- **Robustesse réseau** : l'intercepteur IGDB transforme toute erreur en `IOException` (pas de crash du dispatcher OkHttp), délais d'appel (`callTimeout`) partout, logs HTTP coupés hors debug.
- **Documentation à jour** : ADR datés, guides par fonctionnalité, vérification automatique des liens.

## Constats et propositions

Sévérité : **Haute** = risque réel (sécurité, perte de données, coût) ; **Moy.** = dette qui ralentit ou fragilise ; **Basse** = confort.

### Sécurité

| # | Sév. | Constat | Proposition |
|---|------|---------|-------------|
| S1 | Haute | Le **secret client Twitch** (`TWITCH_CLIENT_SECRET`) est compilé dans l'APK public (`IgdbTokenProvider`). N'importe qui peut l'extraire d'un APK des Releases (`strings`/`jadx`, quelques secondes) et générer des jetons IGDB au nom de l'app : quotas épuisés, voire application Twitch suspendue. C'est plus grave que les clés Steam/TMDB, car c'est un secret d'application et non une simple clé d'API révocable. | Ajouter à `server/` un endpoint `GET /api/igdb-token` (ou un proxy `POST /api/igdb/*`) qui garde le secret dans les variables Vercel et ne renvoie que le jeton d'accès (valable environ 60 jours) ; l'app ne garde que `TWITCH_CLIENT_ID`. Puis **régénérer le secret** sur dev.twitch.tv, puisque l'actuel est déjà sorti dans les APK publiés. |
| S2 | Moy. | Les clés Steam et TMDB sont aussi dans l'APK (assumé et documenté dans `Secrets.kt.example`). | Une fois le proxy de S1 en place, y faire passer Steam aussi (`STEAM_API_BASE_URL` est déjà prévu pour ça). TMDB peut rester côté app (clé v3 gratuite et révocable), mais passer à un jeton v4 « read access » en en-tête plutôt que `api_key=` dans l'URL, qui finit dans les logs HTTP en debug. |
| S3 | Moy. | `POST /api/share` n'a **aucune limite de débit** : un script peut créer des milliers de partages de 2 000 titres et remplir la base Neon (quota gratuit) entre deux purges. | Limite par IP (ex. 20 créations/heure, compteur en table ou Vercel Firewall rate limit), plafond global du nombre de lignes, et taille maximale du corps vérifiée explicitement. |
| S4 | Basse | `share.js` classe une erreur en 400 ou 500 d'après son message (`!e.message.includes('DATABASE')`) : fragile, une erreur Neon au message inattendu pourrait renvoyer son texte au client. | Lever une classe dédiée `ValidationError` dans `validatePayload` et ne renvoyer le message qu'avec cette classe. |
| S5 | Basse | `purge.js` n'exige le jeton que si `CRON_SECRET` est défini : sans lui, l'endpoint est ouvert (sans danger, il ne fait que purger, mais c'est un appel gratuit à la base). | Rendre `CRON_SECRET` obligatoire (401 s'il manque) et le documenter dans [share-server.md](../api/share-server.md). |

### Architecture Android

| # | Sév. | Constat | Proposition |
|---|------|---------|-------------|
| A1 | Moy. | **Neuf `OkHttpClient` distincts** sont construits dans `BacklogApplication` : autant de pools de connexions et de threads, et aucun cache HTTP partagé. | Un client de base unique, dérivé avec `base.newBuilder()` (intercepteurs et délais propres à chaque service) ; ajouter un `Cache` disque de quelques Mo pour IGDB/TMDB/Open Library. |
| A2 | Moy. | Injection de dépendances à la main dans `BacklogApplication` (253 lignes), câblage des ViewModels dans `BacklogNavHost` (630 lignes). Ça marche, mais chaque nouveau service alourdit deux fichiers centraux. | Sans ajouter Hilt : regrouper le câblage dans un `AppContainer` (objet simple avec les services en `lazy`) et des `ViewModelProvider.Factory` par écran. Ça allège le démarrage (rien n'est créé avant d'être utilisé) et le NavHost. |
| A3 | Moy. | Les écrans dépassent souvent 600 lignes (`BacklogScreen` 759, `BooksScreen` 711, `SettingsScreen` 701, `MoviesScreen` 686). | Découper par section (en-tête, liste, feuilles) ; `SettingsScreen` se prête bien à un fichier par carte de réglages. |
| A4 | Moy. | Jeu, film et livre ont chacun leur ViewModel, leur tri, leur filtre et leur partage, avec beaucoup de logique parallèle (`Ranking`, `MovieRanking`, `BookTop` ont la même fonction de déplacement). | Extraire un noyau générique (`moveItem`, tri par nullabilité, filtres de statut) dans `model/` ou `util/`, testé une seule fois. |
| A5 | Basse | Le jeton IGDB est renouvelé à l'expiration, mais pas sur une réponse **401** : un jeton révoqué reste en cache jusqu'à son expiration théorique, et IGDB ne répond plus pendant ce temps. | Passer par un `okhttp3.Authenticator` qui vide le cache et retente une fois sur 401. |
| A6 | Basse | `exportSchema = false` : aucune trace JSON du schéma, donc pas de test de migration automatisé. | `exportSchema = true` + dossier `app/schemas/` versionné + un `MigrationTestHelper` (Robolectric suffit) qui joue 1 → 11. C'est le meilleur filet contre une perte de bibliothèque à la mise à jour. |

### Build, taille et performance

| # | Sév. | Constat | Proposition |
|---|------|---------|-------------|
| B1 | Moy. | `isMinifyEnabled = false` pour `release` et `github` : l'APK embarque tout `material-icons-extended` (plusieurs Mo) et `kotlin-reflect` (via `moshi-kotlin`). | Activer R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) avec des règles pour Moshi/Retrofit/Room ; gain attendu de plusieurs Mo, à mesurer sur l'APK. À tester sur appareil avant de publier. |
| B2 | Basse | `moshi-kotlin` repose sur la réflexion (lent au premier parsing, gros). | Passer à `moshi-kotlin-codegen` via KSP (déjà utilisé pour Room). |
| B3 | Basse | Dépendances de fin 2024 (Compose BOM `2024.09.03`, Room 2.6.1, Kotlin, navigation 2.8.1). | Montée de version groupée, de préférence dans un catalogue `gradle/libs.versions.toml` pour la suite. |
| B4 | Basse | Pas de Baseline Profile : démarrage à froid et premiers défilements plus lents qu'ils ne pourraient l'être. | Ajouter un module `baselineprofile` (Macrobenchmark) quand R8 sera actif. |

### CI et outillage

| # | Sév. | Constat | Proposition |
|---|------|---------|-------------|
| C1 | Moy. | **Les tests du serveur ne tournent pas en CI** : aucun workflow ne lance `npm test` dans `server/`, alors que Vercel déploie automatiquement. | Workflow `server-tests.yml` (`node 20`, `npm ci`, `npm test`) sur les changements de `server/**`. |
| C2 | Basse | `server/package.json` déclare `vibe:analyze` / `vibe:check` vers `scripts/…`, qui n'existe pas dans `server/` (les scripts sont à la racine) : les deux commandes échouent. | Corriger le chemin (`node ../scripts/…`) ou retirer ces entrées. |
| C3 | Basse | Chaque push de n'importe quelle branche recrée une pré-release `debug-<branche>`, jamais nettoyée après suppression de la branche. | Supprimer la release dans un workflow `on: delete`, ou se limiter aux artefacts d'Actions hors PR. |
| C4 | Basse | Pas de couverture mesurée (51 fichiers de tests JVM, aucun test instrumenté). | Kover sur `testDebugUnitTest`, avec un seuil sur `data/` et `model/` (la logique critique visée par `AGENTS.md`). |

## Propositions fonctionnelles

Classées par rapport utilité/effort, en s'appuyant sur ce qui existe déjà :

1. **Scanner un code-barres ISBN** (Google Code Scanner de ML Kit, qui ne demande même pas la permission caméra) : la recherche par ISBN existe déjà, il ne manque que la saisie. Gros gain pour ajouter une pile de livres.
2. **Imports CSV d'autres services** : Goodreads / StoryGraph (livres) et Letterboxd (films), qui exportent tous en CSV. Le moteur d'import CSV et la détection de doublons sont déjà là.
3. **Synchronisation entre appareils** (tablette + téléphone) : le serveur Neon existe déjà ; une sync « dernier écrit gagne » par élément, liée à un compte anonyme (jeton), est réaliste parce que `BacklogRepository` est la seule source de vérité.
4. **Autres bibliothèques de jeux** : GOG, PlayStation ou Xbox (API à vérifier pour chacun, sinon import CSV) ; le modèle `LibraryProvider` est prévu pour.
5. **Suivi des séries épisode par épisode** (ou import Trakt) : les alertes de nouveaux épisodes existent, l'état « saison/épisode vu » serait la suite naturelle.
6. **Statistiques de temps** : avec la durée de jeu IGDB (`game_time_to_beats`) désormais affichée, estimer « temps pour finir mon backlog » et l'ajouter à « Mon année ».

## Plan conseillé

1. **Tout de suite** : S1 (proxy du jeton IGDB + régénération du secret Twitch), C1 (tests serveur en CI), C2.
2. **Ensuite** : S3, A6 (schéma exporté + test de migration), A1, B1.
3. **Au fil de l'eau** : A2–A5, B2–B4, C3–C4, puis les propositions fonctionnelles selon l'envie.
