# Audit UX/UI — Backlog (Android)

Périmètre : Backlog, Recherche, Filtres/Tri, Détail d'un jeu, Découvrir, Réglages, thème.

| # | Sév. | Constat | Correctif |
|---|------|---------|-----------|
| 1 | Haute | Boîtes de dialogue Réglages (sélecteur d'heure, journal debug) sans fond : contenu transparent, illisible. | `Surface` autour du contenu. |
| 2 | Haute | « Retirer du backlog » est un bouton plein (primaire), sans confirmation. Action destructive au même niveau que l'action principale. | Bouton texte rouge + dialogue de confirmation ; « Archiver » en bouton tonal. |
| 3 | Haute | Recherche : le retour système quitte l'app, la requête reste, pas de bouton effacer, pas de focus auto. | `BackHandler`, icône Fermer, croix d'effacement, focus auto, action clavier « Rechercher ». |
| 4 | Haute | Résultats de recherche : « + » actif même si le jeu est déjà ajouté, aucun retour visuel. | Coche si déjà dans le backlog (idem Découvrir). |
| 5 | Moy. | Filtres : chips tronquées à 6 dans un menu, débordement horizontal, filtre actif invisible après fermeture. | Liste complète à radios (menu scrollable), chips de filtres actifs amovibles sous la barre, « Réinitialiser ». |
| 6 | Moy. | Bouton « Archiver » = texte dans un IconButton 48 dp (tronqué, hors norme). | Icônes Archive/Désarchive avec contentDescription ; ligne archivée atténuée. |
| 7 | Moy. | États vides alignés à gauche, sans action. | Centrés, icône + bouton (Rechercher un jeu / Réinitialiser les filtres). |
| 8 | Moy. | Détail : jaquette pleine largeur (~60 % de l'écran), boutons non alignés. | Jaquette 3:4 centrée (max 220 dp), boutons pleine largeur. |
| 9 | Moy. | Thème : seuls primary/background définis → chips, cartes et états sélectionnés teintés lavande M3, en conflit avec la palette crème/terracotta. | Schéma complet clair/sombre (containers, outline, on-*). |
| 10 | Moy. | Réglages : seul l'interrupteur est cliquable ; écran non scrollable (coupé en paysage / grande police). | Ligne entière `toggleable` (Role.Switch), colonne scrollable. |
| 11 | Basse | Loader de recherche décale les résultats ; liste sans date de sortie ; jaquettes manquantes = trou. | Barre linéaire, date en sous-titre, placeholder de jaquette. |
| 12 | Basse | Ajout dans Découvrir = bouton texte, différent de la recherche. | Icône « + » homogène. |

## Second passage (également traité)
| # | Correctif |
|---|-----------|
| 13 | Barre du haut surchargée → `NavigationBar` Backlog / Découvrir / Réglages (visible uniquement sur les 3 écrans racine, état sauvegardé par onglet). |
| 14 | Archivage : Snackbar avec « Annuler ». Ajout (recherche, Découvrir) : Snackbar de confirmation. |
| 15 | Erreur de recherche « HTTP 400… » → messages localisés (réseau / serveur / inconnu). |
| 16 | Export CSV : Snackbar succès / échec. |
| 17 | Détail : nom du jeu dans la barre ; retour automatique après suppression (une fois la ligne supprimée). |

## Reste à traiter
- Grille adaptative sur tablette / paysage.
- Annulation d'une suppression (aujourd'hui : confirmation préalable uniquement).
- Contraste et taille de police à valider avec Accessibility Scanner.

⚠️ Modifications non compilées dans cet environnement (pas de SDK Android) : à valider via la CI `android-build`.
