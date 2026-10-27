# Premier lancement : connecter ses services

**Pour qui / pourquoi** : contributeurs qui touchent à l'accueil ou aux plateformes, et qui veulent savoir quand l'écran « Connecte tes services » s'affiche.

À la toute première ouverture d'une installation neuve, l'app ouvre l'écran **Connecte tes services** par-dessus l'accueil. Il reprend la section *Mes plateformes* des Réglages (Steam, jeux Android, TMDB) pour inciter à importer sa bibliothèque dès le départ. « C'est parti » ou le bouton retour ramène à l'accueil.

## Fonctionnement

- `WelcomeStore` (`ui/welcome/`, DataStore `welcome`) : `takeFirstLaunch()` est vrai une seule fois, puis le repère `platforms_shown` est posé.
- Une **mise à jour** (`lastUpdateTime > firstInstallTime`) ne l'affiche jamais : un utilisateur existant n'y est pas renvoyé.
- Le DataStore `welcome` est **hors sauvegarde** (absent de `data_extraction_rules.xml`) : après une réinstallation, l'écran revient, au moment où il faut justement reconnecter ses services.
- La redirection part de la route d'accueil (`Routes.HOME`, `BacklogNavHost`), donc une fois le graphe de navigation prêt ; la même lambda `platformsContent` sert aux Réglages et à cet écran.

```kotlin
// ui/nav/BacklogNavHost.kt, route HOME
LaunchedEffect(welcomeStore) {
    if (welcomeStore?.takeFirstLaunch() == true) navController.navigate(Routes.WELCOME) { launchSingleTop = true }
}
```
