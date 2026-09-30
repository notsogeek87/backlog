package com.davidgcd.backlog.data.csv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Storage Access Framework helpers for the folder picked with ACTION_OPEN_DOCUMENT_TREE.
 * Uses [DocumentsContract] directly (no androidx.documentfile dependency needed).
 */
object AutoExportFolder {
    const val FILE_NAME = "backlog.csv"
    private const val MIME = "text/csv"

    /** Keeps read+write access to [treeUri] across reboots — required for a background worker. */
    fun takePermission(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun releasePermission(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Already revoked — nothing to release.
        }
    }

    /** False once the user revoked access or the folder's provider dropped the grant. */
    fun hasPermission(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }

    /** Human-readable folder name, or null if the folder is gone. */
    fun displayName(context: Context, treeUri: Uri): String? = try {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /**
     * The export file in the folder, reused when it already exists so each run overwrites
     * the same "backlog.csv" (SAF would otherwise create "backlog (1).csv" every time).
     */
    fun exportFileUri(context: Context, treeUri: Uri): Uri {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == FILE_NAME) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        return DocumentsContract.createDocument(resolver, parent, MIME, FILE_NAME)
            ?: throw java.io.IOException("Could not create $FILE_NAME in $treeUri")
    }
}
