# Guide — Importer sa bibliothèque Steam

**Pour qui / pourquoi** : utilisateurs et développeurs qui veulent importer/tester l'import Steam.

## Configuration développeur
1. Créer une clé sur https://steamcommunity.com/dev/apikey.
2. Dans `config/Secrets.kt` (copié de `Secrets.kt.example`) : `STEAM_API_KEY = "…"`. Les copies locales existantes de `Secrets.kt` doivent recevoir les constantes `STEAM_API_KEY` et `STEAM_API_BASE_URL`.
3. Le profil Steam doit avoir « Détails des jeux » en **public**.

## Utilisation
Réglages → *Mes plateformes* → **Connecter Steam** → connexion sur la page Steam → écran d'import (nouveaux jeux cochés, correspondances incertaines à confirmer, jeux déjà présents listés). **Synchroniser** relance le même écran : temps de jeu mis à jour, nouveaux jeux proposés, rien n'est supprimé.

## Liste de souhaits Steam
Sur la carte Steam (compte connecté), **Synchroniser la liste de souhaits** copie la wishlist Steam dans le backlog avec le statut **Souhaité**.
- Sens unique : Steam → app (l'API Steam ne permet pas d'écrire dans la wishlist). Les jeux sont retrouvés par AppID via IGDB ; ceux qu'IGDB ne connaît pas sont comptés « introuvables ».
- Un jeu déjà dans le backlog garde son statut. Un jeu qui quitte la wishlist est **passé en Backlog** s'il est possédé (après une synchro de la bibliothèque), sinon **archivé** (réversible). Rien n'est jamais supprimé, et une réponse vide (liste privée ou erreur) ne retire rien.
- La wishlist doit être publique (profil Steam → « Détails des jeux » en public).

## Synchronisation automatique

Quand un compte Steam est connecté, l'app synchronise en arrière-plan environ toutes les 12 h (réseau requis) : temps de jeu et liens certains de la bibliothèque, puis la wishlist. Les jeux **pas encore dans le backlog** ne sont jamais ajoutés automatiquement : ils restent à choisir dans l'aperçu de la synchro manuelle.
