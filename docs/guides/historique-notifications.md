# Guide — Historique des notifications (la cloche)

**Pour qui / pourquoi** : utilisateurs qui veulent retrouver une alerte (sortie, promotion, épisode) après avoir effacé la notification, et contributeurs qui ajoutent un nouveau type d'alerte.

## Côté utilisateur
- La **cloche** en haut de l'**Accueil** ouvre « Notifications » : toutes les alertes envoyées par l'app, la plus récente en haut.
- Une **pastille** indique le nombre d'alertes arrivées depuis la dernière visite ; elle retombe à zéro en quittant l'écran. Les nouvelles alertes sont en gras pendant la visite.
- Effacer la notification du volet Android **ne la retire pas** de l'historique.
- Un appui ouvre la fiche du jeu ou de la série.
- L'icône « Tout effacer » vide l'historique (avec confirmation).
- Les rappels de sortie et les changements de date / plateforme sont gardés **même si les notifications système sont refusées**.
- Conservation : 90 jours, 200 alertes au plus. Stocké uniquement sur l'appareil, non inclus dans la sauvegarde.

## Côté code
| Élément | Rôle |
|---------|------|
| `notifications/NotificationHistory.kt` | Règles pures (testées JVM) : `entryId`, ajout sans doublon, purge, non-lus, JSON |
| `notifications/NotificationHistoryStore.kt` | SharedPreferences `notification_history`, flux `changes()` |
| `ReleaseReminderWorker.remember(...)` | Appelé à chaque alerte, à côté du `notify` |
| `ui/notifications/` | `NotificationHistoryScreen` + `NotificationHistoryViewModel` |

Ajouter un type d'alerte : une valeur dans `NotificationKind`, un appel à `remember(...)` dans le worker avec un `detail` qui rend l'alerte unique (prix, épisode, date…), et une icône dans `kindStyle`.

```kotlin
remember(NotificationKind.DEAL, Medium.GAME, game.igdbId.toString(), "${price.finalCents}", game.name, text)
```
