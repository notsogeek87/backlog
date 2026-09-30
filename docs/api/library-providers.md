# API interne — providers de bibliothèque

**Pour qui / pourquoi** : implémenter un nouveau store.

```kotlin
interface GameLibraryProvider {
    val id: String                      // clé stable stockée en base ("steam")
    val igdbExternalSourceId: Int?      // source IGDB external_games (Steam = 1), null si aucune
    suspend fun fetchLibrary(accountId: String): List<LibraryGame>  // lève LibraryException(LibraryError)
    fun existingLinkId(game: GameEntity): String? = null
}
```
- Erreurs : `LibraryError` (`PRIVATE_LIBRARY`, `ACCOUNT_NOT_FOUND`, `UNAVAILABLE`, `RATE_LIMITED`, `NOT_CONNECTED`, `CANCELLED`, `UNKNOWN`). L'UI les traduit (`ui/platforms/LibraryText.kt`) ; le détail technique ne va que dans `AppLogger`.
- `LibrarySyncService.sync(providerId)` → `SyncPreview` (statuts `LINKED`, `EXISTING`, `NEW`, `UNCERTAIN`, `UNMATCHED`) ; `import(providerId, preview, selectedKeys)` → `ImportResult`.
- Enregistrement : ajouter le provider à la map dans `BacklogApplication`.

## Liste de souhaits
`WishlistSource.fetchWishlist(accountId)` (implémenté par `SteamLibraryProvider`, `IWishlistService/GetWishlist`) renvoie des AppID ; `WishlistSyncService.sync()` les résout via IGDB, crée les jeux manquants avec `GameStatus.WISHLIST` et trace la wishlist dans `game_sources` (provider `steam_wishlist`). Résultat : `WishlistSyncResult` (ajoutés / restaurés / promus / archivés / introuvables).
