const KINDS = new Set(['game', 'movie', 'book']);
const esc = (s) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

/**
 * GET /open/<game|movie|book>/<id>  — landing page of a detail page shared from the app.
 * On Android with the app installed the link opens the app directly; otherwise this page offers the
 * backlog:// twin ("Ouvrir dans l'app") and says what the app is. Nothing is stored or looked up.
 */
export default function handler(req, res) {
  const kind = String(req.query.kind ?? '');
  const id = String(req.query.id ?? '');
  if (!KINDS.has(kind) || !id || id.length > 100 || /[^\w:.-]/.test(id)) {
    res.status(404).setHeader('Content-Type', 'text/html; charset=utf-8');
    return res.send('<!doctype html><meta charset="utf-8"><title>Introuvable</title><p>Ce lien n\'existe pas.</p>');
  }
  const appLink = `backlog://open/${kind}/${encodeURIComponent(id)}`;
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.setHeader('Cache-Control', 'public, max-age=0, s-maxage=300');
  res.setHeader('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'");
  res.setHeader('Referrer-Policy', 'no-referrer');
  return res.status(200).send(`<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ouvrir dans Backlog</title>
<style>body{font-family:system-ui,sans-serif;background:#0b0d14;color:#eee;display:grid;place-items:center;min-height:100vh;margin:0;text-align:center}
a.b{display:inline-block;margin-top:1rem;padding:.8rem 1.4rem;border-radius:999px;background:#7c5cff;color:#fff;text-decoration:none;font-weight:600}
p{max-width:28rem;padding:0 1rem;color:#aab}</style></head>
<body><main><h1>Backlog</h1>
<p>Un ami t'a partagé une fiche. Ouvre-la dans l'app pour l'ajouter à ta liste.</p>
<a class="b" href="${esc(appLink)}">Ouvrir dans l'app</a>
<p>L'app n'est pas installée ? Installe Backlog sur Android, puis reviens sur ce lien.</p></main></body></html>`);
}
