# Guide — Importer les jeux installés sur le téléphone

**Pour qui / pourquoi** : utilisateurs et développeurs qui veulent importer les jeux Android installés, comme pour Steam.

## Utilisation
Réglages → *Mes plateformes* → **Jeux installés sur ce téléphone** → **Analyser mon téléphone** → écran d'import (même écran que Steam : nouveaux jeux cochés, correspondances incertaines à confirmer, jeux déjà présents). **Synchroniser** relance l'analyse ; rien n'est supprimé.

## Temps de jeu (optionnel)
Android ne donne le temps d'utilisation qu'avec l'accès spécial « Données d'utilisation ». Le bouton **Autoriser le temps de jeu** ouvre les réglages système. Sans cet accès, le temps de jeu est inconnu (pas affiché) ; avec, le total couvre l'historique conservé par Android (environ un an) et « récent » les 14 derniers jours.

## Fonctionnement
- Fournisseur `android` (`AndroidLibraryProvider`) : aucun compte, l'identifiant externe est le **nom de package**.
- Sont considérées comme jeux les applis lançables déclarées `CATEGORY_GAME` (ou `FLAG_IS_GAME`). Une appli qui ne déclare pas sa catégorie n'est pas détectée.
- Visibilité des applis via `<queries>` (intent LAUNCHER) : pas de permission `QUERY_ALL_PACKAGES`.
- Rapprochement IGDB : `external_games` source 15 (Android, `uid` = package) puis titre, comme Steam.
- Tout reste local : aucune donnée sur les applis installées ne quitte le téléphone, hormis les titres cherchés sur IGDB.
