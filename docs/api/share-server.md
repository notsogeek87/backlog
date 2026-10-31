# API du serveur de partage (`server/`)

**Pour qui / pourquoi** : contributeurs qui touchent au partage par lien (`ShareLinkService` côté app), au jeton IGDB (`IgdbTokenProvider`) ou au petit serveur Vercel + Neon qui les héberge.

## Vue d'ensemble
Fonctions serverless Node ≥ 20 (`server/api/`), base Postgres Neon (Francfort, `fra1`), schéma dans `server/schema.sql` (tables `shares`, `rate_limits`, `app_tokens` ; script idempotent, à rejouer tel quel après chaque changement). L'app reste locale : le serveur ne stocke qu'un **instantané** à durée de vie limitée. Si le serveur est injoignable, l'app se replie sur un partage texte.

| Route | Fichier | Rôle |
|-------|---------|------|
| `POST /api/share` | `api/share.js` | Crée un lien. Corps `{title?, items[]}` → `201 {id, token, url}`. Le jeton n'est renvoyé qu'une fois (seul son hash SHA-256 est stocké). |
| `PUT /api/share?id=<id>` | `api/share.js` | Met à jour le contenu, même lien. En-tête `x-share-token`. **N'étend pas** l'expiration. |
| `DELETE /api/share?id=<id>` | `api/share.js` | Supprime le lien (`204`). |
| `GET /b/:id` | `api/view.js` | Page publique HTML (CSP stricte, `Referrer-Policy: no-referrer`). `404` si inconnu ou expiré. |
| `GET /open/<game\|movie\|book>/:id` | `api/open.js` | Page d'arrivée d'une fiche partagée ; ouvre l'app si installée (App Links), sinon le bouton « Ouvrir dans l'app » (lien `intent://` Android) lance l'app ou, si elle est absente, ouvre https://github.com/notsogeek87/backlog (`browser_fallback_url`) ; sur ordinateur, lien direct vers GitHub et affiche les détails de la fiche (affiche, année, genres, synopsis, note, liens TMDB / IMDb / IGDB / catalogue du livre) lus dans la query string du lien (`t`, `y`, `c`, `g`, `s`, `r`, `m`, `u`, construite par `DetailPreview` côté app, nettoyée par `lib/openPage.js`). Ne stocke rien. |
| `GET /.well-known/assetlinks.json` | `api/assetlinks.js` | Vérification des App Links Android. |
| `GET /api/igdb-token` | `api/igdb-token.js` | Jeton Twitch pour IGDB → `200 {client_id, access_token, expires_in}`. Le secret Twitch ne quitte jamais le serveur ; un seul jeton partagé (table `app_tokens`), renouvelé 24 h avant son expiration. `?stale=<empreinte>` (16 premiers hex de sha256 du jeton) signale un jeton refusé par IGDB : renouvelé si Twitch confirme qu'il est mort. `503` si le serveur n'est pas configuré. |
| `GET /api/purge` | `api/purge.js` | Cron quotidien (`17 3 * * *`, voir `vercel.json`) : liens expirés et vieux compteurs de limite. **Exige** `CRON_SECRET` (sinon `401` à tout appel). |

## Règles à connaître
- **Durée de vie : 24 h après la création** (`SHARE_TTL_HOURS`, `server/lib/shareCore.js`), sans renouvellement. `PUT`/`DELETE` sur un lien expiré répondent `404` ; l'app crée alors un nouveau lien.
- **Limites** (`lib/rateLimit.js`, compteurs par hash d'IP, jamais l'IP brute) : 20 créations de lien par heure et par client, 30 demandes de jeton IGDB par heure → `429`. Au-delà de `MAX_LIVE_SHARES` (5 000) liens vivants, la création répond `503`. Corps de plus de 2 Mo → `413`. Mettre à jour un lien (`PUT`) n'est pas limité.
- Erreurs : seule une `ValidationError` (charge utile invalide) renvoie son message (`400`) ; toute autre erreur répond `500 erreur serveur` sans détail.
- Charge utile validée par `validatePayload` (max `MAX_ITEMS` = 2000 éléments, hôtes d'images autorisés dans `IMAGE_HOSTS`).
- Payload `kind: "library"` : une page avec un onglet par typologie (Jeux, Films & séries ; onglets CSS sans script).

## Variables d'environnement (Vercel)
| Variable | Rôle |
|----------|------|
| `DATABASE_URL` | Connexion Neon. |
| `TWITCH_CLIENT_ID`, `TWITCH_CLIENT_SECRET` | Application Twitch Developer (https://dev.twitch.tv/console/apps) : sans elles, IGDB est indisponible dans l'app. |
| `CRON_SECRET` | Jeton du cron de purge (Vercel l'envoie en `Authorization: Bearer`). |
| `ASSETLINKS_SHA256` | Empreintes de certificat supplémentaires pour les App Links (voir `api/assetlinks.js`). |

## Exemple
```bash
# Créer
curl -X POST https://<domaine>/api/share -H 'content-type: application/json' \
  -d '{"title":"Mon backlog","items":[{"name":"Hades","status":"BACKLOG"}]}'
# → {"id":"…","token":"…","url":"https://<domaine>/b/…"}

# Mettre à jour (même lien)
curl -X PUT "https://<domaine>/api/share?id=<id>" -H "x-share-token: <token>" \
  -H 'content-type: application/json' -d '{ … }'
```
La forme exacte des éléments est définie par `validatePayload`.

## Tests
```bash
cd server && npm test   # node --test test/*.test.js
```
