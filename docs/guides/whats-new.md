# Fenêtre « Nouveautés » après une mise à jour

**Pour qui / pourquoi** : contributeurs qui ajoutent une fonctionnalité visible et doivent la faire annoncer aux utilisateurs.

Après l'installation d'une nouvelle version, l'app affiche une seule fois les nouveautés apparues depuis la version précédemment installée.

## Ajouter une nouveauté

Ajouter une entrée **en fin de liste** dans `Changelog.entries` (`ui/whatsnew/Changelog.kt`) avec le prochain `id` (jamais réutilisé ni réordonné) :

```kotlin
ChangelogEntry(7, "Titre court", "Une phrase qui explique ce que ça change pour l'utilisateur."),
```

## Fonctionnement

- `WhatsNewStore` mémorise (DataStore `whats_new`) le dernier `id` présenté. Au lancement, `WhatsNewPrompt` affiche les entrées d'`id` supérieur, de la plus récente à la plus ancienne, puis met le repère à jour.
- Les versions CI (`0.1.<run>`) ne sont pas connues à l'avance : c'est l'`id` d'entrée, pas le numéro de version, qui borne « ce qui est nouveau ».
- Première installation : rien n'est affiché, le repère est seulement enregistré.
- Mise à jour depuis une version d'avant ce journal : on part de `Changelog.LEGACY_BASELINE_ID`.
