<!--
Merci d'utiliser ce template pour vos Pull Requests.
Supprimez les sections non pertinentes si nécessaire.
---
 -->

## 📌 **Titre de la PR**

*Format: `feat: description` / `fix: description` / `docs: description` / `refactor: description` / `chore: description`*

---

## 📝 **Description**

### Contexte
*Pourquoi cette modification ? Quel problème résout-elle ? À quelle issue/fonctionnalité est-elle liée ?*

### Modifications
*Liste concise des changements apportés:*
- [ ] Ajout de...
- [ ] Modification de...
- [ ] Suppression de...
- [ ] Correction de...

---

## 🔍 **Type de Changement**

- [ ] ✅ **Fonctionnalité** (nouvelle fonctionnalité)
- [ ] 🐛 **Correction** (fix d'un bug)
- [ ] 📚 **Documentation** (mise à jour de la doc)
- [ ] 🔧 **Refactor** (refactor de code sans changement fonctionnel)
- [ ] ⚙️ **Configuration** (changement de config CI/CD, build, etc.)
- [ ] 🧹 **Nettoyage** (suppression de code mort, dépendances, etc.)
- [ ] 🔒 **Sécurité** (fix de vulnérabilité)
- [ ] 📦 **Dépendance** (mise à jour de dépendance)

---

## 📂 **Fichiers Modifiés**

*Liste des fichiers principaux modifiés et leur rôle:*
```
app/src/main/java/com/davidgcd/backlog/...  # Description
server/api/...                              # Description
docs/...                                   # Description
```

---

## ✅ **Checklist Pré-Merge**

### Code
- [ ] Le code compile sans erreurs
- [ ] Tous les tests passent (`testDebugUnitTest`, `npm test`)
- [ ] Le lint passe (`lintDebug`)
- [ ] Pas de secrets committés
- [ ] Les messages de commit sont clairs et en français

### Documentation
- [ ] La documentation est mise à jour (si nécessaire)
- [ ] Les liens dans la doc sont valides (`check_docs_links.py`)
- [ ] Les ADR sont créés/mis à jour pour les décisions architecturales

### Tests
- [ ] Les tests existants passent
- [ ] De nouveaux tests sont ajoutés pour la nouvelle logique
- [ ] La couverture de test est maintenue/améliorée

### Architecture
- [ ] `BacklogRepository` reste la seule source de vérité
- [ ] Pas de logique métier dans les ViewModel
- [ ] Pas d'appels réseau directs depuis l'UI
- [ ] Les couches MVVM sont respectées

---

## 🧪 **Comment Tester**

*Instructions pour tester cette PR:*
```bash
# Commandes à exécuter
./gradlew testDebugUnitTest
cd server && npm test
python3 tools/check_docs_links.py

# Étapes manuelles
1. Lancer l'app sur un émulateur
2. Naviguer vers...
3. Vérifier que...
```

---

## 📊 **Impact**

### Performance
- [ ] Amélioration des performances
- [ ] Impact neutre
- [ ] Dégradation acceptable (à justifier)

### Compatibilité
- [ ] Compatible avec toutes les versions supportées
- [ ] Nécessite une mise à jour de `minSdk` (à justifier)
- [ ] Casses des API internes (à documenter)

### Sécurité
- [ ] Aucune implication sécurité
- [ ] Améliore la sécurité
- [ ] ⚠️ **Nécessite une review sécurité** (détails: ...)

---

## 🔗 **Issues & Références**

*Lier les issues, discussions ou documents pertinents:*
- Closes #
- Related to #
- Documentation: `docs/...`
- ADR: `docs/architecture/...`

---

## 📸 **Screenshots / Captures**

*Si la PR modifie l'UI, ajouter des captures avant/après:*

| Avant | Après |
|-------|-------|
| (capture avant) | (capture après) |

---

## 💬 **Notes pour les Reviewers**

*Points spécifiques à vérifier, questions, ou contextes supplémentaires:*
- 

---

## 🚀 **Post-Merge**

*Actions à effectuer après le merge:*
- [ ] Mettre à jour la documentation utilisateur (si applicable)
- [ ] Vérifier le déploiement CI
- [ ] Publier une release (si applicable)
- [ ] Communiquer aux utilisateurs (si changement visible)
