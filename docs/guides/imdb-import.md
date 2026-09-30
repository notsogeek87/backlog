# Guide — Films & séries et compte IMDb

**Pour qui / pourquoi** : utiliser l'onglet Films/séries et récupérer sa watchlist et ses notes IMDb.

## Utiliser l'onglet
- **Films/séries** (barre du bas) : ta liste, avec pastilles Tous / Films / Séries, statuts (À voir, En cours, Vu), tri, filtres, archivage, classement personnel (icône podium) et partage texte.
- **Rechercher** (loupe) : recherche IMDb, `+` pour ajouter.
- **Découvrir** → *Films & séries* : films/séries populaires et meilleurs.
- Fiche d'un titre : statut, **ma note /10**, note IMDb, synopsis, « Ouvrir sur IMDb ».

## Importer son compte IMDb
1. Réglages → *Mes plateformes* → **IMDb** → *Se connecter à IMDb* (ou, liste vide, *Importer depuis IMDb*).
2. Connecte-toi sur la page IMDb (l'app ne voit pas ton mot de passe).
3. L'import démarre : notes → *Vu*, watchlist → *À voir*. Relançable via *Synchroniser*.
4. Session expirée : *Me reconnecter*. *Déconnecter* oublie la session sur l'appareil (la liste locale est gardée).

```kotlin
// Cœur de l'import (data/imdb/ImdbSyncService.kt)
val result: ImdbSyncResult = imdbSyncService.sync()
```

Architecture et limites : [ADR](../architecture/2026-09-30-films-series-imdb.md).
