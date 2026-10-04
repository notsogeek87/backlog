# API du serveur de partage (`server/`)

**Pour qui / pourquoi** : contributeurs qui touchent au partage par lien (`ShareLinkService` côté app) ou au petit serveur Vercel + Neon qui l'héberge.

## Vue d'ensemble
Fonctions serverless Node ≥ 20 (`server/api/`), base Postgres Neon (Francfort, `fra1`), schéma dans `server/schema.sql` (table `shares`). L'app reste locale : le serveur ne stocke qu'un **instantané** à durée de vie limitée. Si le serveur est injoignable, l'app se replie sur un partage texte.

| Route | Fichier | Rôle |
|-------|---------|------|
| `POST /api/share` | `api/share.js` | Crée un lien. Corps `{title?, items[]}` → `201 {id, token, url}`. Le jeton n'est renvoyé qu'une fois (seul son hash SHA-256 est stocké). |
| `PUT /api/share?id=<id>` | `api/share.js` | Met à jour le contenu, même lien. En-tête `x-share-token`. **N'étend pas** l'expiration. |
| `DELETE /api/share?id=<id>` | `api/share.js` | Supprime le lien (`204`). |
| `GET /b/:id` | `api/view.js` | Page publique HTML (CSP stricte, `Referrer-Policy: no-referrer`). `404` si inconnu ou expiré. |
| `GET /open/<game\|movie\|book>/:id` | `api/open.js` | Page d'arrivée d'une fiche partagée ; ouvre l'app si installée (App Links), sinon propose `backlog://`. Ne stocke rien. |
| `GET /.well-known/assetlinks.json` | `api/assetlinks.js` | Vérification des App Links Android. |
| `GET /api/purge` | `api/purge.js` | Cron quotidien (`17 3 * * *`, voir `vercel.json`). Protégé par `CRON_SECRET` s'il est défini. |

## Règles à connaître
- **Durée de vie : 24 h après la création** (`SHARE_TTL_HOURS`, `server/lib/shareCore.js`), sans renouvellement. `PUT`/`DELETE` sur un lien expiré répondent `404` ; l'app crée alors un nouveau lien.
- Charge utile validée par `validatePayload` (max `MAX_ITEMS` = 2000 éléments, hôtes d'images autorisés dans `IMAGE_HOSTS`).
- Payload `kind: "library"` : une page avec un onglet par typologie (Jeux, Films & séries ; onglets CSS sans script).

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
