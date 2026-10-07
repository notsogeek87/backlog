# Guide — Navigation, recherche et gestes

**Pour qui / pourquoi** : utilisateurs qui veulent connaître tous les raccourcis de l'app, et contributeurs qui touchent à la navigation ou aux listes.

## Les quatre onglets
| Onglet | Contenu |
|--------|---------|
| **Accueil** | « Ce soir, je fais quoi ? », à continuer (jeux joués, films/séries en cours, livres en cours), sorties des 30 prochains jours, ajouts récents, « Mon année » |
| **Bibliothèque** | Jeux · Films & séries · Livres (un sélecteur sous la barre du haut ; le choix est partagé avec Découvrir) |
| **Découvrir** | Listes IGDB / TMDB / Open Library, gardées 10 minutes en mémoire (affichées tout de suite, rafraîchies en arrière-plan) |
| **Réglages** | Plateformes, apparence, notifications, données, mises à jour, à propos |

Sous 600 dp de large : barre du bas ; au-delà (pliable ouvert, tablette, paysage) : rail latéral.

## Recherche
La loupe de la Bibliothèque ouvre un champ avec deux portées :
- **Ma liste** : filtre instantané, accents et casse ignorés, tous les mots doivent correspondre (titre, auteur, distribution…). Aucun appel réseau. Portée par défaut quand la liste n'est pas vide.
- **Catalogue** : recherche en ligne pour ajouter ; les résultats des deux autres médias suivent (« Aussi dans… »).

Une recherche vide dans « Ma liste » propose « Chercher dans le catalogue ».

## Filtrer et trier
Icône filtre → feuille unique : tri, affichage (Tous / Souhaités / Possédés pour les jeux), statut, genre, plateforme, archivés. Les changements s'appliquent derrière la feuille ; le bouton du bas annonce « Voir N résultats ». Les tuiles de stats du haut sont aussi des filtres de statut (un second toucher les retire). Les filtres actifs restent visibles en pastilles retirables.

## Gestes sur les lignes (jeux, films & séries)
| Geste | Effet |
|-------|-------|
| Balayer vers la droite | Statut suivant (Backlog → Joué → Terminé ; À voir → En cours → Vu ; Souhaité → Backlog) |
| Balayer vers la gauche | Archiver / désarchiver |
| Toucher le badge de statut | Menu des statuts |
| Appui long | Actions rapides : statut, archiver, placer en tête du classement (jeux), partager, retirer |

Chaque changement affiche un Snackbar « Annuler ». Les livres (grille de jaquettes) : badge de statut et appui long (favori, classement, partage, retrait). Pour TalkBack, les mêmes actions sont des actions personnalisées de la ligne.

**« + » d'un résultat** : un toucher ajoute avec le statut par défaut (« Souhaité » pour un jeu pas encore sorti), un appui long propose « Ajouter comme… ».

## Fiches détail
- « Ajouter » est une barre collée en bas ; la bande-annonce est en tête de page.
- Retirer ne demande pas de confirmation : un Snackbar « Annuler » apparaît sur la liste.
- Le fond de la fiche prend la couleur dominante de la jaquette.
- Jeux : note de 1 à 10, proposée quand on passe un jeu à « Terminé ».

## Classement (jeux)
Le classement type backlog ne concerne que les **jeux** ; films, séries et livres se jugent à leurs notes (1–10, 1–5 étoiles). Jeux : Bibliothèque > menu « ⋮ » > Mon classement. Tirer une ligne par la poignée (l'écran défile seul près des bords) ; **doubles flèches** = tout en haut / tout en bas ; **toucher le numéro** = « placer à la position N » (utile avec des centaines d'entrées). Un classement est écrit dans Room une seule fois, au dépôt.

## Ce soir, je fais quoi ?
Carte de l'accueil : média (Tout / Jeux / Films & séries / Livres) × temps (moins de 1 h 30 / une soirée / sans limite) → trois idées (`model/Tonight`). Ordre : déjà commencé, puis ce qui traîne depuis longtemps, bien noté et qui tient dans le temps. « Autre idée » change le tirage. Films : durée TMDB ; livres : pages ÷ 40 par heure ; **jeux : durée inconnue, toujours proposés**.

## Mon année
Accueil → « Mon année » : terminés, ajouts, heures et pages, genres préférés, coups de cœur (parmi ce qui est noté). Partageable en **image** (la carte, via `FileProvider`) ou en texte. La date de fin est retenue depuis cette version (`completedAt`, aussi dans le CSV) ; avant, un élément terminé est daté de son ajout.

## Raccourcis, widget, partage vers l'app
- **Appui long sur l'icône** : Ajouter un jeu / un film / un livre, Mon année (`res/xml/shortcuts.xml`, liens `backlog://action/…`).
- **Widget** « Backlog : en cours » (Glance) : en cours + prochaine sortie ; mis à jour quand l'app passe en arrière-plan et toutes les 30 min.
- **Partager vers Backlog** (texte) : un lien TMDB ouvre la fiche ; un lien Steam, IGDB, Goodreads ou Open Library, un ISBN ou un simple titre ouvre la recherche pré-remplie (`util/SharedText`).

## Alertes
Ajoutées au contrôle quotidien existant (`ReleaseReminderWorker`) :
- **Promotions Steam** : jeux « Souhaité » avec un `steamAppId`, remise ≥ 20 %, une fois par prix (`DealRules`, `SteamPriceService`, API publique de la boutique).
- **Nouveaux épisodes** : séries « À voir / En cours », épisode diffusé depuis moins de 3 jours et pas déjà annoncé (`EpisodeRules`, TMDB `/tv/{id}`).
Un lot de 25 éléments tourne chaque jour (`AlertBatches`). Interrupteurs dans Réglages > Changements.

## Apparence
Réglages > Apparence : Système / Sombre / Clair / AMOLED, couleurs dynamiques (Android 12+), aperçu animé des bandes-annonces (désactivé par défaut : données mobiles). Voir [design system](../architecture/design-system.md).
