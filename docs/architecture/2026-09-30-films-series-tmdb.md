# ADR — Films & séries via TMDB

**Pour qui / pourquoi** : contributeurs qui touchent à l'onglet Films/séries ou à la connexion TMDB, et toute personne qui se demande pourquoi ce module n'utilise pas IGDB comme les jeux.

## Contexte
Les jeux viennent d'IGDB. Pour les films et séries, une première version lisait IMDb (sans API publique : endpoints non documentés, pages scrapées, cookies de session rejoués). Elle a été remplacée le jour même par **TMDB**, qui a une API officielle gratuite, des contenus en français, et une connexion de compte sanctionnée par un vrai flux d'autorisation.

## Décisions
- **API officielle TMDB v3** (`TmdbClient`, `language=fr-FR`) : recherche (`/search/multi`), détails (`/movie|tv/{id}?append_to_response=credits`), listes populaires / mieux notées. Parsing pur et testé dans `TmdbParsers`.
- **Clé d'API** : `Secrets.TMDB_API_KEY` (gitignoré, secret CI `TMDB_API_KEY`), embarquée dans l'APK comme la clé Steam → clé dédiée et révocable. Placeholder = fonctionnalité « indisponible », jamais de crash.
- **Identifiant = clé `movie:603` / `tv:1396`** (`TitleKey`) : TMDB numérote films et séries séparément. Table `movies` (`MovieEntity`, colonne `titleKey`, migration 6→7 qui reconstruit la table de la version IMDb, vieille de quelques heures).
- **Connexion = flux request-token TMDB** : `/authentication/token/new` → page d'autorisation `themoviedb.org/authenticate/<token>` dans un WebView (redirection interceptée, comme Steam) → `/authentication/session/new`. L'app ne voit jamais le mot de passe. Le `session_id` (le vrai secret) est stocké dans `noBackupFilesDir` (`FileTmdbSessionStore`), donc **hors sauvegarde Android/transfert d'appareil** ; le DataStore (sauvegardé) ne garde que l'id/pseudo du compte. Déconnexion = `DELETE /authentication/session` + effacement local.
- **Import additif** (`TmdbSyncService`) : titre noté = *Vu* + note ; watchlist = *À voir*. Jamais de suppression ni d'écrasement d'un statut/rang/note posé dans l'app.
- **Envoi initial, une fois par compte** (`TmdbSyncService.uploadLocal`) : à la première synchronisation d'un compte lié, les notes absentes de TMDB et les titres *À voir* absents de la watchlist sont envoyés (archivés et *En cours* exclus). Marqueur `localUploadedAt` du compte (`LibraryAccountStore`), posé seulement si tout l'envoi a réussi ; pas de renvoi aux synchronisations suivantes, pour ne pas ré-ajouter un titre retiré sur themoviedb.org.
- **Listes du compte** (`TmdbClient.userLists / isInList / setInList`, `MovieRepository.tmdbLists`, carte `TmdbListsCard`) : la watchlist (`account_states`, `/account/{id}/watchlist`) et les listes créées par l'utilisateur (`/account/{id}/lists`, `/list/{id}` lu page par page pour savoir ce qu'elle contient, `add_item`, `remove_item` ; `item_status` n'est pas utilisé : il ne connaît que les films, une série de la liste ne serait jamais cochée ; le contenu lu est gardé 2 min dans `MovieRepository` et tenu à jour par nos écritures), cochables depuis la fiche d'un titre. Contrairement à l'écriture au mieux ci-dessous, ces actions sont explicites : la coche est optimiste et revient en arrière avec un message si TMDB refuse. Après un ajout depuis une liste ou une recherche, `TmdbListsPrompt` (fourni par `BacklogNavHost`) ouvre `TmdbListsSheet` pour cocher les autres listes, seulement s'il y en a. Ajouter un doublon à une liste compte comme réussi. Une liste dont l'état ne peut pas être lu s'affiche décochée sans cacher les autres.
- **Index des listes** (`MovieRepository.refreshTmdbListsIndex`, `tmdbListsIndex`, `FileTmdbListsIndexStore`) : watchlist + listes de l'utilisateur avec leurs titres (`/list/{id}` en entier, `TmdbClient.listTitles`), relu à l'ouverture de Films & séries (au plus toutes les 2 min), gardé dans `filesDir/tmdb_lists_index.json` (hors sauvegarde, reconstruit depuis TMDB). Les titres absents de Room sont ajoutés en *À voir* (additif, jamais d'écrasement) : la bibliothèque contient tout ce qui est dans une liste. Filtre `MovieFilter.tmdbListId` (0 = watchlist). Sans session, l'index est vidé ; hors ligne, il reste tel quel.
- **Écriture vers TMDB, au mieux** (`MovieRepository`) : noter un titre dans l'app le note sur TMDB ; passer un titre en *À voir* l'ajoute à la watchlist TMDB, le quitter l'en retire. Sans session/réseau, l'action locale réussit quand même (best effort, rien n'est bloquant).
- **Où regarder ?** : `/{movie|tv}/{id}/watch/providers`, région fixée à `FR` (`WatchProviders.REGION`), jamais mis en cache ni stocké (les offres changent) ; « free » et « ads » fusionnés. Erreur réseau → la carte disparaît. Crédit JustWatch obligatoire affiché sous la carte.
- **Navigation** : onglet « Films/séries » à côté de « Jeux » ; Découvrir a un sélecteur Jeux / Films & séries.
- **Attribution** : la mention exigée par TMDB est affichée dans la carte TMDB des Réglages.

## Limites connues
- Sans clé TMDB dans le build, l'onglet Films/séries affiche des erreurs de chargement et la connexion est refusée avec un message clair.
- Pas (encore) d'export/import CSV des films, ni de rappels de sortie pour les films.
- Les notes TMDB vont de 0,5 à 10 ; l'app les arrondit à 1–10 en import.

## Page de partage

Le serveur `server/` gère aussi les films & séries : un payload avec `kind: "movies"` (nom, statut, série, année, affiche TMDB, lien TMDB, note 1–10) est validé puis rendu par `renderMoviesPage` (affiches notées, meilleure note en premier, puis les non notés par statut). Lien distinct de celui des jeux (`ShareLinkService.publishMovies`, clés `movies_share_*`). La CSP de `/b/:id` autorise `image.tmdb.org`.
