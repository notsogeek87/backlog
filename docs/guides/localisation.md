# Localisation (français uniquement)

**Pour qui / pourquoi** : contributeurs qui ajoutent des chaînes ou du contenu affiché.


L'app est en français uniquement : `values/strings.xml` est en français et la
locale est forcée à `fr` (`MainActivity`, `BacklogApplication`). Les noms de genres/
plateformes IGDB, le nom des jeux et le verdict Steam ("Overwhelmingly
Positive"…) restent tels que l'API les renvoie, jamais traduits — même
règle que l'app iOS sur le contenu API. `BacklogSort`/`ReleaseReminderSchedule`
stockent un nom d'enum stable ; leur libellé affiché est résolu à l'écran
via `stringResource`, jamais stocké traduit. Limite connue : les messages
d'erreur de recherche (`BacklogViewModel.searchError`) et les noms de jeu
non résolus lors d'un import CSV restent en anglais pour l'instant — les
traduire demanderait de faire circuler un `Context`/`Resources` jusque
dans les ViewModels, non fait dans ce premier passage.

