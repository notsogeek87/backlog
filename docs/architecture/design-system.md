# Design system "Glass night"

**Pour qui / pourquoi** : contributeurs qui ajoutent ou modifient un écran et doivent réutiliser les mêmes jetons et composants.

Surfaces translucides sur un fond nuit, déclinées en quatre apparences : **Sombre** (défaut), **Clair**, **AMOLED** (noir pur) et **Système** ; plus des **couleurs dynamiques** optionnelles (Android 12+). Jetons : `ui/theme/Theme.kt` (`Glass`, `GlassPalette`), composants : `ui/components/`.

## Jetons
Les neutres (`Glass.Text`, `TextMuted`, `Bg`, `Border`, `GlassTop`…) et l'accent (`Glass.Cyan`, `Accent`, `OnAccent`) sont **lus dans la composition** (`@Composable`) : ils suivent l'apparence choisie dans Réglages (`UiPreferences.themeMode`). Les teintes de statut (`Glass.Blue`, `Green`, `Amber`…) sont constantes ; `readableOnDark(tint)` les éclaircit (sombre) ou les assombrit (clair) pour le texte. Ne jamais écrire une couleur en dur dans un écran : ajouter un rôle à `GlassPalette`.

## Règles d'accessibilité
- Cible tactile ≥ 48 dp (les pills le sont via `minimumInteractiveComponentSize`).
- Tout `clickable` porte un `role` ; une sélection se déclare avec `selectable` (jamais seulement une couleur).
- Une jaquette cliquable seule (carrousel) a un `contentDescription` = le titre ; dans une ligne, elle est décorative.
- Contraste ≥ 4,5:1 pour le texte : le texte du bouton principal vient de `Glass.OnAccent`.
- Un geste (balayage, glisser) a toujours son équivalent en actions personnalisées TalkBack.

| Composant | Rôle |
|-----------|------|
| `AppBackground` | Fond `#050A14` + halos radiaux cyan/violet |
| `Modifier.glass()` / `GlassCard` | Surface verre : dégradé blanc translucide, bordure fine, coins 20 dp |
| `GameCover` | Jaquette 3:4, ombre, bordure |
| `GameListItem` | Ligne unique (backlog, recherche, Découvrir) : jaquette, badges plateformes, date, barre de note |
| `GlassBadge` / `GlassPill` | Badges (plateformes, genres, statut) / pills de filtres actifs |
| `GradientButton` / `GlassButton` | CTA principal (dégradé d'accent) / action secondaire |
| `StatCard` | Tuile de stat ; avec `onClick`, bascule d'un filtre de statut |
| `FilterSortSheet` / `QuickActionsSheet` | Feuille « Filtrer et trier » / feuille d'actions d'un appui long |
| `SwipeActionRow` | Ligne balayable (statut suivant / archiver) |
| `StatusBadgeMenu` | Badge de statut qui ouvre un menu (statut en deux touchers) |
| `AddButton` | « + » : toucher = statut par défaut, appui long = « Ajouter comme… » |
| `LibrarySearchField` / `SearchScopePills` / `LibraryEmptyState` / `RemovableChip` / `SectionHeader` | Pièces communes aux trois bibliothèques |
| `StickyActionBar` / `DetailBackdrop` / `TrailerHero` | Action collée, fond teinté par la couleur dominante, bande-annonce en tête de fiche |
| `SkeletonList` | Lignes fantômes pendant un chargement |
| `GradientProgressBar` | Barre de score 0–100 avec halo cyan |
| `StatCard` | Tuile de statistique |
| Barre du bas | Verre translucide, actif cyan |

## Données réelles uniquement
L'app ne stocke ni temps de jeu, ni progression, ni listes personnalisées, ni profil : ces éléments du mockup **ne sont pas affichés**.
Utilisés à la place : nombre de jeux actifs/archivés (tuiles), ajouts récents (carrousel), note IGDB / Metacritic / Steam (barres dégradées), plateformes, genres, date de sortie.

## Écrans
- **Accueil « Aujourd'hui »** : « Ce soir, je fais quoi ? », à continuer, sorties à venir, ajouts récents, « Mon année ».
- **Bibliothèque** : sélecteur Jeux / Films & séries / Livres ; tuiles stats cliquables, carrousel « Ajouts récents », grille adaptative, recherche « Ma liste / Catalogue », feuille de filtres, balayage et appui long.
- **Fiche jeu** : artwork flouté en fond (blur API 31+), grande jaquette, badges, carte de notes, résumé, actions.
- **Découvrir / Réglages** : mêmes surfaces verre.

## Limites connues
- Pas de vrai `backdrop-filter` en Compose : le verre est simulé (dégradé + bordure). Le flou réel n'est utilisé que sur les artworks.
- Le thème clair est une déclinaison (même verre, bordures et fond adaptés) : le contraste y reste à mesurer sur appareil.
- Non compilé localement (pas de SDK Android) : validation par la CI `android-build`.

## Logo et identité visuelle
Identité (octobre 2026) : une **claquette ouverte** (films), une **manette** sur l'ardoise (jeux) et un **marque-page cyan** (livres), sur fond bleu nuit, avec le mot-symbole « backlog » (« log » en dégradé cyan→bleu) et la ligne « Films · Jeux vidéo · Livres ». L'app n'utilise que **l'emblème** (sans texte ni petites icônes) ; les couleurs de l'identité sont celles du thème (fond nuit, accent cyan→bleu).
- Sources : `docs/brand/logo.png` (identité complète, 1254×1254), `docs/brand/emblem.png` (emblème détouré, fond transparent), `docs/brand/logo-512.png`, `docs/brand/play-store-icon-512.png` (fiche Play Store, emblème sur fond nuit, 512×512).
- Android : icône adaptative (`mipmap-anydpi-v26/ic_launcher*.xml` : fond uni `#0A0F1E`, emblème dans la zone sûre de 66 %, variante monochrome Android 13+ en aplat), splash Android 12+ (`values-v31/themes.xml`), marque in-app `drawable-nodpi/ic_logo_mark.png` (barres du haut, états vides).
- L'emblème a été détouré depuis l'image d'identité (fond sombre retiré par remplissage depuis les bords, bords adoucis) : pour le refaire, partir de `logo.png`.
