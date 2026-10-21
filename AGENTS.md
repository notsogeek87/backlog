# Guide pour les Agents IA - Backlog

## ⚠️ RÈGLE FONDAMENTALE POUR TOUS LES AGENTS IA
**L'agent code SEUL, de A à Z.**
- Il **ne demande JAMAIS** à l'utilisateur de coder.
- Il **vérifie systématiquement** que :
  - Le code compile (`./gradlew assembleDebug`, `npm test`, etc.).
  - **TOUS les tests passent** avant tout push.
- Si un choix d'architecture bloque, il pose **une seule question courte** pour trancher.

---

## Introduction

Ce document est un guide pour **tous les agents IA** (Vibe Code, GitHub Copilot, etc.) qui travaillent sur ce repository. Il définit les conventions, restrictions et bonnes pratiques à suivre.

---

## Contexte du Projet

**Backlog** est une application Android de gestion de bibliothèque multimédia (jeux vidéo, films, séries, livres) avec un serveur de partage.

- **Langue principale**: Français (UI et documentation)
- **Architecture**: MVVM (Modèle-Vue-VueModèle)
- **Plateforme**: Android (Kotlin + Jetpack Compose)
- **Backend**: Node.js + PostgreSQL (Neon serverless)

---

## Règles Universelles

### 1. Sécurité
- **❌ INTERDIT**: Commiter des secrets (clés API, mots de passe, tokens)
- **❌ INTERDIT**: Exposer des secrets dans les logs, messages d'erreur ou URL
- **✅ OBLIGATOIRE**: Utiliser les fichiers `.example` comme templates
- **✅ OBLIGATOIRE**: Vérifier `.gitignore` avant d'ajouter de nouveaux fichiers

### 2. Conventions de Code

#### Kotlin (Android)
```kotlin
// ✅ BON
class GameEntity(
    val id: Long,
    val title: String,
    val platform: String
)

// ❌ MAUVAIS
class game_entity {  // PascalCase pour les classes
    var ID: Long = 0  // camelCase, val par défaut
}
```

#### JavaScript/TypeScript (Serveur)
```javascript
// ✅ BON
import { Game } from './models/game.js';

export async function getGame(id) {
    // ...
}

// ❌ MAUVAIS
const get_game = async (id) => {  // camelCase pour les fonctions
    // ...
}
```

#### Nommage
- **Classes/Interfaces**: `PascalCase` (ex: `GameRepository`, `BacklogViewModel`)
- **Variables/Fonctions**: `camelCase` (ex: `getGameById`, `gameTitle`)
- **Constantes**: `UPPER_SNAKE_CASE` (ex: `MAX_ITEMS`, `API_BASE_URL`)
- **Fichiers**: `kebab-case.md` ou `snake_case.kt` selon la convention du langage

### 3. Architecture

#### Couches Android
```
┌─────────────────┐
│      UI          │  ← Jetpack Compose
│    (ui/)         │
├─────────────────┤
│   ViewModel      │  ← Logique de présentation
│    (ui/)         │
├─────────────────┤
│   Repository     │  ← Source unique de vérité
│    (data/)       │
├─────────────────┤
│   Data Sources   │  ← API locales (Room) et distantes
│    (data/local,  │
│     data/remote) │
└─────────────────┘
```

**Règles:**
- `BacklogRepository` est la **seule source de vérité** entre Room et les API
- Pas de logique métier dans les ViewModel
- Pas d'appels réseau directs depuis l'UI

#### Serveur (server/)
```
server/
├── api/      # Endpoints HTTP (Express-like via Vercel)
├── lib/      # Logique métier et utilitaires
├── test/     # Tests unitaires
└── schema.sql # Schéma PostgreSQL
```

---

## Workflows

### Développement Local

#### Android
```bash
# Prérequis
- Android Studio (JDK 17)
- Gradle 8.9

# Setup
1. Copier app/src/main/java/com/davidgcd/backlog/config/Secrets.kt.example
   vers Secrets.kt
2. Remplir les clés API (Twitch, Steam, TMDB)
3. Lancer dans Android Studio ou via ./gradlew

# Commandes utiles
./gradlew assembleDebug          # Build APK debug
./gradlew testDebugUnitTest      # Tests unitaires
./gradlew lintDebug             # Lint
```

#### Serveur
```bash
# Prérequis
- Node.js >= 20
- npm

# Setup
cd server
npm install

# Développement
npm test                        # Lancer les tests
node api/open.js               # Démarrer un endpoint localement

# Déploiement
# Automatique via Vercel (lié au repo GitHub)
```

### CI/CD

Le projet utilise **GitHub Actions** avec 3 workflows:

1. **android-build.yml**: Build + tests à chaque push/PR
   - Lint
   - Tests unitaires
   - Build APK debug
   - Publication release GitHub (main uniquement)

2. **docs-links.yml**: Vérification des liens Markdown
   - Exécute `tools/check_docs_links.py`

3. **play-store-bundle.yml**: Publication sur Google Play
   - Déclenchement manuel
   - Build AAB signé
   - Publication sur le track spécifié

---

## Documentation

### Structure
```
docs/
├── README.md              # Point d'entrée
├── architecture/          # ADR, architecture, design system
│   ├── overview.md        # Architecture globale
│   ├── design-system.md   # Design system "Glass night"
│   └── *.md               # ADR (format AAAA-MM-JJ-sujet.md)
├── api/                   # Documentation des API
│   ├── library-providers.md
│   ├── books.md
│   └── ...
└── guides/                # Guides pratiques
    ├── steam-import.md
    ├── tmdb-import.md
    └── ...
```

### Règles de Documentation
- **Format**: Markdown
- **En-tête**: Titre + ligne "**Pour qui / pourquoi**: ..."
- **Exemples**: Inclure des extraits de code quand pertinent
- **Liens**: Toujours relatifs, vérifiés par `check_docs_links.py`
- **Mise à jour**: Toute nouvelle fonctionnalité doit être documentée

---

## Bonnes Pratiques

### Git
- **Messages de commit**: En français, clairs et concis
  - ✅ `feat: ajouter import Steam`
  - ❌ `fixed stuff`
- **Branches**: `feat/nom-fonctionnalite` ou `fix/nom-probleme`
- **PR**: Utiliser le template `.github/PULL_REQUEST_TEMPLATE.md`

### Tests
- **Couverture**: Viser 100% pour la logique métier critique
- **Nommage**: `*Test.kt` pour les tests Kotlin, `*.test.js` pour Node
- **Structure**: Un fichier de test par classe/module

### Performance
- **Cache**: Utiliser le cache mémoire existant (stale-while-revalidate)
- **Room**: Éviter les requêtes N+1, utiliser les `@Transaction`
- **Images**: Coil gère déjà le cache, ne pas réimplémenter

### Sécurité
- **API Keys**: Toujours injectées via `Secrets.kt` (gitignored)
- **HTTPS**: Toujours, pas de HTTP en production
- **Permissions**: Demander le minimum nécessaire (AndroidManifest)

---

## Ressources

### API Utilisées
| Service | API | Documentation |
|---------|-----|---------------|
| IGDB | Twitch API | https://api-docs.igdb.com |
| Steam | Steam Web API | https://developer.valvesoftware.com/wiki/Steam_Web_API |
| TMDB | TMDB API v3 | https://developers.themoviedb.org |
| Open Library | Open Library API | https://openlibrary.org/developers/api |
| Google Books | Google Books API | https://developers.google.com/books |

### Outils Internes
- **scripts/vibe-analyze.js**: Analyse complète du repo
- **scripts/vibe-check.js**: Vérifications pré-commit
- **tools/check_docs_links.py**: Vérifie les liens Markdown
- **tools/publish_play_store.py**: Publication Google Play

### Dépendances Clés

#### Android (app/build.gradle.kts)
- **Compose**: `androidx.compose:compose-bom:2024.09.03`
- **Room**: `androidx.room:room-runtime:2.6.1`
- **Retrofit**: `com.squareup.retrofit2:retrofit:2.11.0`
- **Coil**: `io.coil-kt:coil-compose:2.7.0`

#### Serveur (server/package.json)
- **Neon**: `@neondatabase/serverless:^1.0.0`
- **Node**: `>=20`

---

## Dépannage

### Problèmes Courants

#### Android
- **Erreur de signature**: Vérifier que `app/debug.keystore` existe
- **Tests qui échouent**: Lancer `./gradlew clean testDebugUnitTest`
- **Build lent**: Activer le cache Gradle (`gradle.properties`)

#### Serveur
- **Erreur Neon**: Vérifier la connexion PostgreSQL
- **Tests qui échouent**: Vérifier les variables d'environnement

#### Documentation
- **Liens brisés**: Exécuter `python3 tools/check_docs_links.py`

### Où Demander de l'Aide
1. **Documentation**: Voir `docs/` et `README.md`
2. **Code existant**: Chercher des exemples similaires dans le codebase
3. **Issues GitHub**: Ouvrir une issue avec reproduction détaillée

---

## Checklist pour les Agents IA

Avant de proposer une solution:
- [ ] J'ai lu et compris le contexte du projet
- [ ] J'ai vérifié les fichiers existants pour des exemples
- [ ] Ma solution respecte les conventions de code
- [ ] Ma solution ne contient pas de secrets
- [ ] J'ai inclus la documentation nécessaire
- [ ] J'ai pensé aux tests unitaires
- [ ] J'ai vérifié que ma solution ne casse pas le build existant

---

## Mises à Jour de ce Document

Ce document doit être mis à jour lorsque:
- De nouvelles conventions sont adoptées
- De nouvelles dépendances majeures sont ajoutées
- L'architecture change significativement
- De nouveaux workflows CI/CD sont ajoutés

**Dernière mise à jour**: 2026-10-20
