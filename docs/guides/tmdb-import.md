# Guide — Films & séries et compte TMDB

**Pour qui / pourquoi** : utiliser l'onglet Films/séries et lier son compte TMDB (watchlist et notes).

## Prérequis (une fois, côté développeur)
1. Créer un compte sur https://www.themoviedb.org puis demander une clé : https://www.themoviedb.org/settings/api (« API Key (v3 auth) »).
2. Local : `TMDB_API_KEY` dans `Secrets.kt` (copie de `Secrets.kt.example`). CI : secret de dépôt `TMDB_API_KEY`.

## Utiliser l'onglet
- **Films/séries** (barre du bas) : ta liste, pastilles Tous / Films / Séries, statuts (À voir, En cours, Vu), tri, filtres, archivage, classement personnel et partage texte.
- **Rechercher** (loupe) : recherche TMDB, `+` pour ajouter.
- **Découvrir** → *Films & séries* : populaires et mieux notés.
- Fiche d'un titre : statut, **ma note /10**, note TMDB, synopsis, réalisateur, distribution, « Ouvrir sur TMDB ».

## Lier son compte TMDB
1. Réglages → *Mes plateformes* → **TMDB** → *Se connecter à TMDB* (ou, liste vide, *Importer depuis TMDB*).
2. Connecte-toi sur la page TMDB puis **Approuve** l'accès (l'app ne voit pas ton mot de passe).
3. L'import démarre : notes → *Vu*, watchlist → *À voir*. Relançable via *Synchroniser*.
4. Ensuite, noter un titre (ou le passer en/hors *À voir*) met aussi à jour ton compte TMDB.
5. *Déconnecter* ferme la session sur TMDB et sur l'appareil ; la liste locale est conservée.

```kotlin
// Cœur de l'import (data/tmdb/TmdbSyncService.kt)
val result: TmdbSyncResult = tmdbSyncService.sync()
```

Architecture et limites : [ADR](../architecture/2026-09-30-films-series-tmdb.md).
