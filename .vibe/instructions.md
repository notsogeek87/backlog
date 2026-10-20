# Instructions Vibe Code - Backlog

## Contexte du Projet

**Backlog** est une application Android de gestion de bibliothèque de jeux vidéo, films, séries et livres, inspirée de l'app iOS [Backlog](https://github.com/loicgff/backlog).

### Stack Technique
- **Frontend**: Kotlin + Jetpack Compose (UI native Android)
- **Architecture**: MVVM (Modèle-Vue-VueModèle)
- **Persistance**: Room (base de données locale, équivalent SwiftData)
- **Réseau**: Retrofit + Moshi pour les API (IGDB/Twitch, Steam, TMDB, Open Library)
- **Images**: Coil pour le chargement et cache des jaquettes
- **Backend**: Serveur Node.js + Neon (PostgreSQL serverless) pour le partage de listes
- **CI/CD**: GitHub Actions (build Android, tests, publication Play Store)

### Domaines Fonctionnels
1. **Jeux vidéo**: Import depuis Steam, IGDB (via API Twitch)
2. **Films & Séries**: Import depuis TMDB avec connexion utilisateur
3. **Livres**: Recherche Open Library / Google Books, gestion des éditions
4. **Partage**: Génération de liens partagés via serveur Vercel

### Points d'Attention
- **Secrets**: Jamais committer `Secrets.kt` (gitignored). Utiliser `Secrets.kt.example` comme template.
- **Localisation**: UI en français, contenu API (noms de jeux, genres...) jamais traduit
- **BacklogRepository**: Source unique de vérité entre Room et les API distantes
- **Versioning**: `versionCode` strictement croissant, basé sur `github.run_number` en CI

---

## Instructions pour Vibe Code

### Règles Générales
1. **Préserver l'architecture MVVM** existante
2. **Respecter les conventions** du codebase (noms de fichiers, structure, style)
3. **Toujours mettre à jour la documentation** (`docs/`) pour toute nouvelle fonctionnalité
4. **Ne jamais committer de secrets** ou de clés API
5. **Privilégier les petites PR** ciblées et bien décrites

### Workflow de Développement
```bash
# Avant de pousser
./gradlew testDebugUnitTest           # Tests unitaires Android
cd server && npm test                 # Tests serveur Node.js
python3 tools/check_docs_links.py     # Vérification des liens de la doc

# Analyse du repo
npm run vibe:analyze                  # Analyse complète
npm run vibe:check                    # Vérifications pré-commit
```

### Priorités d'Optimisation
1. **Performance**: Cache disque pour l'onglet Découvrir
2. **Tests**: Tests instrumentés (androidTest) pour navigation et permissions
3. **Cloud**: Sync multi-appareils (Firebase ou backend perso)
4. **Metacritic**: Branchement d'un vrai fournisseur RapidAPI
5. **Localisation**: Traduction des messages d'erreur restants

### Conventions de Code
- **Kotlin**: Suivre les conventions Android/Kotlin standard
- **Noms**: `PascalCase` pour les classes, `camelCase` pour les variables/fonctions
- **Tests**: Un fichier de test par classe, suffixé par `Test.kt`
- **Documentation**: Format Markdown, titre + "Pour qui / pourquoi" en en-tête

### Commandes Utiles
```bash
# Build Android
./gradlew assembleDebug

# Lint
./gradlew lintDebug

# Tests
./gradlew testDebugUnitTest

# Serveur (dans server/)
npm install
npm test
node api/open.js  # Démarrer le serveur localement

# Vérification liens docs
python3 tools/check_docs_links.py
```

### Structure des Fichiers
```
backlog/
├── app/                  # Application Android
│   ├── src/main/        # Code source
│   │   ├── java/com/davidgcd/backlog/
│   │   │   ├── config/  # Configuration (Secrets.kt.example)
│   │   │   ├── data/    # Couche données (Repository, API, DB)
│   │   │   ├── model/   # Modèles de données
│   │   │   ├── ui/      # Composants Compose
│   │   │   └── util/    # Utilitaires
│   └── build.gradle.kts
├── server/               # Serveur de partage (Node.js)
│   ├── api/             # Endpoints API
│   ├── lib/             # Logique métier
│   └── package.json
├── docs/                 # Documentation
│   ├── architecture/     # ADR et architecture
│   ├── api/             # Documentation API
│   └── guides/           # Guides utilisateur/développeur
├── tools/                # Scripts Python
└── .github/workflows/    # CI/CD
```

### Contacts & Ressources
- **Repository**: https://github.com/notsogeek87/backlog
- **App iOS originale**: https://github.com/loicgff/backlog
- **API IGDB**: https://api-docs.igdb.com
- **API TMDB**: https://developers.themoviedb.org
- **API Steam**: https://developer.valvesoftware.com/wiki/Steam_Web_API
- **API Open Library**: https://openlibrary.org/developers/api

---

## Checklist Pré-Commit

- [ ] Code compile sans erreurs
- [ ] Tous les tests passent (`testDebugUnitTest`, `npm test`)
- [ ] Pas de secrets dans les fichiers modifiés
- [ ] Documentation mise à jour si nécessaire
- [ ] Messages de commit en français, clairs et concis
- [ ] Vérification `vibe:check` passée
- [ ] Lint Android (`lintDebug`) passé
