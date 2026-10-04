# Notes Metacritic (scaffold, non branché)

**Pour qui / pourquoi** : contributeurs qui branchent un fournisseur Metacritic sur `MetacriticApi`.


Metacritic n'a pas d'API officielle gratuite (l'app iOS passe par un proxy
payant qu'elle a construit elle-même). `MetacriticApi`/`MetacriticService`
sont codés avec la même architecture que Steam/IGDB (interface Retrofit,
DTO, appel réseau, rapprochement de titre via `TitleSimilarity`), mais le
endpoint (`MetacriticApi.search`) et la forme de la réponse
(`MetacriticSearchResult`) sont un **gabarit** à ajuster une fois que tu as
un vrai abonnement RapidAPI — cherche "metacritic" sur rapidapi.com, mets
son host/clé dans `Secrets.kt`, et adapte l'interface au contrat exact de
ce fournisseur. Tant que ce n'est pas fait, la section Notes affiche
simplement la carte Steam seule (aucune erreur, aucun texte "indisponible" —
même règle que HowLongToBeat sur iOS).

Le lien Steam (`GameEntity.steamAppId`) est extrait automatiquement du
champ IGDB `websites` (catégorie 13) au premier chargement d'un jeu, et
persisté — pas besoin de le renseigner à la main.

