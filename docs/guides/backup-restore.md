# Sauvegarde et restauration (Android / Samsung)

**Pour qui / pourquoi** : utilisateurs qui changent de téléphone et contributeurs qui touchent aux règles de backup.


La bibliothèque suit l'utilisateur quand il change de téléphone, sans compte ni
serveur : `allowBackup` + règles explicites.

- **Sauvegarde Google (Auto Backup)** et **transfert d'appareil à appareil**
  (câble / Wi-Fi Direct, dont **Samsung Smart Switch** et Samsung Cloud, qui
  respectent les mêmes règles) : `res/xml/data_extraction_rules.xml`
  (Android 12+) et `res/xml/backup_rules.xml` (Android 8-11). Les deux fichiers
  doivent rester identiques (vérifié par `BackupRulesTest`).
- **Sauvegardé** : la base Room `backlog.db` (jeux, statuts, classement,
  sources Steam/Android), les réglages de notifications, les comptes de
  bibliothèque liés (SteamID public uniquement) et le lien de partage.
- **Non sauvegardé** : caches (jaquettes Coil), planning WorkManager (recréé au
  démarrage) et le dossier d'export CSV automatique (sa permission SAF ne peut
  pas être restaurée : à re-choisir dans Réglages).
- `BacklogBackupAgent` force un checkpoint WAL avant la copie pour que la base
  sauvegardée soit complète.
- Tester : `adb shell bmgr backupnow com.davidgcd.backlog`, désinstaller puis
  réinstaller (`adb shell bmgr restore`), ou
  `adb shell bmgr fullbackup com.davidgcd.backlog`. Le CSV (Réglages →
  Exporter) reste la sauvegarde manuelle portable.

