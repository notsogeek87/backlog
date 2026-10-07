# Documentation Backlog

**Pour qui / pourquoi** : point d'entrée de toute la documentation du projet. Pour démarrer, voir le [README](../README.md) puis [CONTRIBUTING](../CONTRIBUTING.md).

## Organisation

| Dossier | Contenu |
|---------|---------|
| `architecture/` | ADR datés (`AAAA-MM-JJ-sujet.md`), architecture de l'app, design system, audits |
| `api/` | Fournisseurs de données et API (IGDB, Steam, TMDB, livres, Metacritic) et serveur de partage |
| `guides/` | Procédures : imports, sauvegarde, localisation, tests, bandes-annonces, nouveautés |
| `legacy/` | Documentation obsolète conservée (vide pour l'instant) |
| `brand/` | Logos et visuels (pas de texte) |

## Sommaire

### Architecture
- [Architecture de l'app (MVVM)](architecture/overview.md)
- [Design system « Glass night »](architecture/design-system.md)
- [Audit UX/UI (octobre 2026)](architecture/ux-audit.md)
- ADR : [import de bibliothèques de jeux](architecture/2026-09-29-library-providers.md) · [films et séries TMDB](architecture/2026-09-30-films-series-tmdb.md) · [livres Open Library](architecture/2026-10-01-livres-open-library.md)

### API
- [Fournisseurs de bibliothèque (Steam…)](api/library-providers.md)
- [Livres](api/books.md)
- [Metacritic (scaffold)](api/metacritic.md)
- [Serveur de partage](api/share-server.md)

### Guides
- [Import Steam](guides/steam-import.md) · [Import Android](guides/android-import.md) · [Import TMDB](guides/tmdb-import.md) · [Livres](guides/books.md)
- [Sauvegarde et restauration](guides/backup-restore.md)
- [Localisation](guides/localisation.md)
- [Tests](guides/testing.md)
- [Navigation, recherche et gestes](guides/navigation-et-gestes.md)
- [Bandes-annonces](guides/trailers.md)
- [Fenêtre « Nouveautés »](guides/whats-new.md)

## Écrire de la doc
Chaque page commence par un titre et une ligne **Pour qui / pourquoi**, avec des exemples de code quand c'est utile. Un choix structurant donne un ADR dans `architecture/`, une route ou fonction une page dans `api/`, une procédure une page dans `guides/`. Toute nouvelle page est ajoutée au sommaire ci-dessus.
