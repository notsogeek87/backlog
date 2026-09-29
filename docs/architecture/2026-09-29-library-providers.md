# ADR — Import de bibliothèques de jeux (Steam en premier)

**Pour qui / pourquoi** : contributeurs qui ajoutent une plateforme (Epic, GOG, Xbox…) ou touchent à l'import Steam.

## Contexte
L'app est locale (Room, pas de backend ni de comptes). Les jeux sont indexés par `igdbId`.

## Décisions
- **Pas d'entité `SteamGame`** : la table `game_sources` (`provider`, `externalId`, `igdbId`, temps de jeu, `lastSyncedAt`, `missingFromLibrary`) rattache N sources à un même jeu. Pas de clé étrangère vers `games` (le `REPLACE` du DAO la supprimerait en cascade) : une source orpheline = jeu retiré du backlog par l'utilisateur.
- **Abstraction `GameLibraryProvider`** (`data/library`) : un provider ne sait que *lister* une bibliothèque. Matching IGDB, dédoublonnage et persistance sont dans `LibrarySyncService`.
- **Connexion Steam = OpenID 2.0** (WebView sur `steamcommunity.com`, redirection interceptée, assertion vérifiée par `check_authentication`). Seul le SteamID64 + pseudo sont stockés (DataStore). Aucun mot de passe ne transite par l'app.
- **Matching** (du plus sûr au moins sûr) : source déjà liée → `steamAppId` historique → IGDB `external_games` → titre identique (auto) → titre approchant (`GameMatcher`, proposé à l'utilisateur) → recherche IGDB.
- **Jamais** de suppression, ni d'écrasement de statut/archivage/dates : seule la ligne `game_sources` est écrite.

## Limite connue : clé API Steam
`GetOwnedGames` exige une clé. Sans backend, elle vit dans `Secrets.kt` (donc dans l'APK). Atténuation : `STEAM_API_BASE_URL` peut pointer vers un proxy qui ajoute la clé (laisser `STEAM_API_KEY` vide). La clé est ajoutée par un intercepteur placé après le logging : jamais loggée.
