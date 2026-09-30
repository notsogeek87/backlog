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
