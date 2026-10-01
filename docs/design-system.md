# Design system "Glass night"

Dark-only, translucent surfaces over a night-blue canvas. Tokens: `ui/theme/Theme.kt` (`Glass`), components: `ui/components/Glass.kt`.

| Composant | Rôle |
|-----------|------|
| `AppBackground` | Fond `#050A14` + halos radiaux cyan/violet |
| `Modifier.glass()` / `GlassCard` | Surface verre : dégradé blanc translucide, bordure fine, coins 20 dp |
| `GameCover` | Jaquette 3:4, ombre, bordure |
| `GameListItem` | Ligne unique (backlog, recherche, Découvrir) : jaquette, badges plateformes, date, barre de note |
| `GlassBadge` / `GlassPill` | Badges (plateformes, genres, statut) / pills de filtres actifs |
| `GradientButton` / `GlassButton` | CTA principal (cyan→bleu→violet) / action secondaire |
| `GradientProgressBar` | Barre de score 0–100 avec halo cyan |
| `StatCard` | Tuile de statistique |
| Barre du bas | Verre translucide, actif cyan |

## Données réelles uniquement
L'app ne stocke ni temps de jeu, ni progression, ni listes personnalisées, ni profil : ces éléments du mockup **ne sont pas affichés**.
Utilisés à la place : nombre de jeux actifs/archivés (tuiles), ajouts récents (carrousel), note IGDB / Metacritic / Steam (barres dégradées), plateformes, genres, date de sortie.

## Écrans
- **Backlog (accueil)** : tuiles stats, carrousel « Ajouts récents », grille adaptative (1 colonne mobile, plusieurs sur tablette/desktop), tri/filtres/recherche/archivage conservés.
- **Fiche jeu** : artwork flouté en fond (blur API 31+), grande jaquette, badges, carte de notes, résumé, actions.
- **Découvrir / Réglages** : mêmes surfaces verre.

## Limites connues
- Pas de vrai `backdrop-filter` en Compose : le verre est simulé (dégradé + bordure). Le flou réel n'est utilisé que sur les artworks.
- Pas de thème clair (dark-only volontaire).
- Non compilé localement (pas de SDK Android) : validation par la CI `android-build`.

## Logo
Une étagère avec un objet par média, aux silhouettes distinctes : un livre (tranche), un film (claquette + lecture) et un jeu (manette), en bleus-gris et teal sur fond bleu nuit. Sobre, sans mascotte.
- Sources : `docs/brand/logo.png` (logo complet, 1254×1254), `docs/brand/logo-512.png`, `docs/brand/play-store-icon-512.png` (fiche Play Store, 512×512).
- Android : icône adaptative (`mipmap-anydpi-v26/ic_launcher*.xml`, avec variante monochrome Android 13+), splash Android 12+ (`values-v31/themes.xml`), marque in-app `drawable-nodpi/ic_logo_mark.png` (en-tête Backlog, état vide).
