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

