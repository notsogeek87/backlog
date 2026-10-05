# Guide — Bandes-annonces

**Pour qui / pourquoi** : utilisateurs qui regardent une bande-annonce depuis une fiche, et contributeurs qui touchent à la sélection (VF/VO) ou au lecteur.

## Côté utilisateur
- Fiche **jeu** (IGDB) et fiche **film/série** (TMDB) : carte « Bande-annonce (VF) » ou « (VO) ».
- La **vignette** s'affiche d'abord ; rien n'est chargé depuis YouTube avant le toucher sur ▶. Le lecteur intégré démarre alors dans la fiche.
- « Ouvrir dans YouTube » reste disponible quand le propriétaire interdit l'intégration.
- Aucune carte si le titre n'a pas de vidéo. Les bandes-annonces sont récupérées en direct à chaque ouverture, jamais mises en cache.

## Choix de la vidéo
| Source | Endpoint | Règle |
|--------|----------|-------|
| Jeux (IGDB) | `v4/game_videos` (`fields video_id,name; where game = <id>; limit 20;`) | Pas de champ langue : VF reconnue au **titre** (`fr`, `vf`, `vostfr`, `français`, `french`), de préférence si c'est aussi un « trailer / bande-annonce » ; sinon un titre « trailer » ; sinon la première vidéo. |
| Films/séries (TMDB) | `/movie\|tv/{id}/videos?include_video_language=fr,en,null` | Uniquement `site = YouTube` et `type = Trailer` ; la version `fr` d'abord, sinon une autre langue. |

Les deux produisent un `Trailer(youtubeKey, isFrench)` (`model/Trailer.kt`) ; `isFrench` choisit le libellé VF/VO.

```kotlin
// Jeux : IgdbService.getTrailer → GameVideo.pickTrailer(videos)
val trailer: Trailer? = repository.trailer(igdbId)

// Films/séries : TmdbClient.trailer → TmdbParsers.parseTrailer(json)
val trailer: Trailer? = movieRepository.trailer(titleKey)
```

## Lecteur (`ui/components/TrailerCard.kt`)
- `WebView` avec une iframe `youtube-nocookie.com/embed/<id>` (sous-titres français demandés).
- Le HTML est chargé avec `loadDataWithBaseURL("https://www.youtube-nocookie.com", …)` : sans base URL https (donc sans referrer), YouTube refuse l'intégration (erreur 153).
- Le plein écran passe par la fenêtre de l'activité.

## Tests
`GameVideoTest` couvre `pickTrailer` ; `FakeIgdbApi.gameVideos` renvoie une liste vide (pas de bande-annonce dans les tests de la fiche). Voir [Tests](testing.md).
