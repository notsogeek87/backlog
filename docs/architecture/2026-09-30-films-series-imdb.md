# ADR — Films & séries via IMDb

**Pour qui / pourquoi** : contributeurs qui touchent à l'onglet Films/séries ou à l'import IMDb, et toute personne qui se demande pourquoi ce module ne ressemble pas à celui des jeux (IGDB).

## Contexte
Les jeux viennent d'IGDB (API officielle). IMDb n'a **pas d'API publique gratuite**. Le besoin : gérer films et séries comme les jeux (liste, statut, note, classement, découverte, partage) et pouvoir se connecter à son compte IMDb.

## Décisions
- **Table `movies` séparée** (`MovieEntity`, migration 5→6), clé = id IMDb texte (`tt…`). Ne pas mélanger avec `games` (clé numérique IGDB). Statuts propres : `WatchStatus` (À voir / En cours / Vu), `TitleKind` (film / série) pour le sous-filtre.
- **Pas d'API officielle** : `ImdbClient` appelle les mêmes endpoints que le site : suggestion JSON (`v3.sg.media-imdb.com`) pour la recherche et les affiches, JSON-LD des pages `/title/tt…/` pour le détail, pages `/chart/…` (JSON-LD, sinon `__NEXT_DATA__`) pour Découvrir. Tout le parsing est dans `ImdbParsers`/`ImdbCsv` (fonctions pures, testées) : c'est **le seul endroit à corriger si IMDb change ses pages**.
- **Connexion = WebView** sur la page de connexion IMDb (IMDb, Amazon…). L'app ne voit jamais le mot de passe : elle détecte le cookie de session (`at-main`), le rejoue via `WebViewCookieJar` (OkHttp) et lit les **exports CSV** de l'utilisateur (`/user/ur…/ratings/export`, `/list/ls…/export`). Le compte (id `ur…`) est stocké dans `LibraryAccountStore` sous le fournisseur `imdb`. Se déconnecter efface les cookies du WebView.
- **Import additif** (`ImdbSyncService`) : titre noté = *Vu* + note ; titre en watchlist = *À voir*. Jamais de suppression, jamais d'écrasement d'un statut/rang/note posé dans l'app (seul *À voir* → *Vu* si noté sur IMDb).
- **Affiches** : absentes des CSV → complétées à la volée (`MovieRepository.backfillPosters`, `""` = IMDb n'en a pas).
- **Navigation** : onglet « Films/séries » à côté de « Jeux » ; Découvrir a un sélecteur Jeux / Films & séries.

## Limites connues
- **Lecture seule côté IMDb** : ajouter/noter dans l'app ne modifie pas le compte IMDb (aucune écriture possible sans API officielle).
- Endpoints non documentés : peuvent changer ou refuser des clients « non navigateur » (l'app envoie un User-Agent Chrome mobile). Échec = message d'erreur, jamais de crash ni de perte de données.
- Pas (encore) de CSV export/import des films, de page de partage publique, ni de rappels de sortie pour les films.
