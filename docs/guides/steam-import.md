# Guide — Importer sa bibliothèque Steam

**Pour qui / pourquoi** : utilisateurs et développeurs qui veulent importer/tester l'import Steam.

## Configuration développeur
1. Créer une clé sur https://steamcommunity.com/dev/apikey.
2. Dans `config/Secrets.kt` (copié de `Secrets.kt.example`) : `STEAM_API_KEY = "…"`. Les copies locales existantes de `Secrets.kt` doivent recevoir les constantes `STEAM_API_KEY` et `STEAM_API_BASE_URL`.
3. Le profil Steam doit avoir « Détails des jeux » en **public**.

## Utilisation
Réglages → *Mes plateformes* → **Connecter Steam** → connexion sur la page Steam → écran d'import (nouveaux jeux cochés, correspondances incertaines à confirmer, jeux déjà présents listés). **Synchroniser** relance le même écran : temps de jeu mis à jour, nouveaux jeux proposés, rien n'est supprimé.
