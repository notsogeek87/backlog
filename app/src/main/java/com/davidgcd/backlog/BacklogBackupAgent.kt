package com.davidgcd.backlog

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import com.davidgcd.backlog.data.local.AppDatabase

/**
 * Full-backup only (Google Auto Backup, Samsung Smart Switch / Samsung Cloud, device transfer): what
 * gets saved is decided by backup_rules.xml / data_extraction_rules.xml. This agent only makes sure
 * the Room database is a single self-contained file when the copy starts — Room runs in WAL mode, so
 * recent writes could otherwise sit in `backlog.db-wal` and be lost if that file isn't captured.
 */
class BacklogBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        checkpointDatabase()
        super.onFullBackup(data)
    }

    // Key/value backup is not used; required by the abstract class.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    private fun checkpointDatabase() {
        try {
            AppDatabase.get(this).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        } catch (e: Exception) {
            // Best effort: the -wal/-shm files are part of the backup rules anyway.
            Log.w(TAG, "WAL checkpoint before backup failed", e)
        }
    }

    private companion object {
        const val TAG = "BacklogBackupAgent"
    }
}
