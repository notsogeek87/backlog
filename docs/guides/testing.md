# Tests

**Pour qui / pourquoi** : contributeurs qui lancent ou ajoutent des tests unitaires.


`app/src/test/` — tourne dans `testDebugUnitTest` (celui que la CI exécute),
sans émulateur :
- `TitleSimilarityTest`, `ReleaseDateFormattingTest` — logique pure.
- `GameDaoTest` — Room en mémoire **sous Robolectric**, pour avoir un vrai
  test de DAO sans matériel connecté.
- `BacklogViewModelTest` — `FakeGameDao`/`FakeIgdbApi` (interfaces, donc
  fakables sans mock ni Room), couvre le tri, le filtre archivés/genre et
  la distinction "backlog vide" vs "résultats filtrés vides".


## Composants Compose sous Robolectric
`ui/ComponentsSmokeTest` et `ui/ComposeInteractionsTest` rendent les composants du design system dans chaque thème et rejouent les gestes (balayage, glisser-déposer du classement, feuille de filtres) **sans émulateur**, dans la même tâche `testDebugUnitTest`. Deux pièges : `setContent` ne s'appelle qu'une fois par test (piloter le thème par un état), et les feuilles modales vivent dans leur propre fenêtre où Robolectric ne route pas les touchers : déclencher `SemanticsActions.OnClick` plutôt que `performClick`.
