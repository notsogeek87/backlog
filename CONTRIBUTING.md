# Contribuer

**Pour qui / pourquoi** : toute personne qui build, teste ou modifie l'app Android ou le serveur de partage.

## Démarrer
1. Ouvrir le dossier dans Android Studio (JDK 17), laisser Gradle synchroniser.
2. Copier `app/src/main/java/com/davidgcd/backlog/config/Secrets.kt.example` vers `Secrets.kt` (gitignored) et renseigner les clés (Twitch/IGDB obligatoire, `STEAM_API_KEY`, TMDB…). Détails dans le [README](README.md#démarrer).
3. Lancer sur un émulateur ou un appareil (minSdk 26).

## Vérifier avant de pousser
```bash
gradle testDebugUnitTest           # tests unitaires (ceux de la CI, sans émulateur) ; ou ./gradlew si le wrapper est généré
cd server && npm test                # serveur de partage (Node ≥ 20)
python3 tools/check_docs_links.py    # liens relatifs des .md (aussi en CI : docs-links.yml)
```
La CI (`.github/workflows/android-build.yml`) build et teste chaque push ; `play-store-bundle.yml` publie sur Google Play, uniquement en déclenchement manuel.

## Conventions
- Messages de commit et UI en **français** ; contenu API (noms de jeux, genres…) jamais traduit. Voir [localisation](docs/guides/localisation.md).
- Respecter les couches décrites dans [l'architecture](docs/architecture/overview.md) : `BacklogRepository` est la seule source de vérité, un seul point de logging (`AppLogger`), un seul constructeur d'URL d'images (`IgdbImage`).
- Jamais de secret committé.

## Documentation
Toute fonctionnalité ou modification d'API met à jour `docs/` dans la même PR. Règles de rangement et sommaire : [docs/README.md](docs/README.md).
