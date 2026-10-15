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

    /** Fichier d'export déjà présent dans le dossier : son nom exact, ou une copie « backlog (N).csv ». */
    data class Entry(val documentId: String, val name: String, val lastModified: Long)

    /** Où écrire, et quels doublons supprimer une fois l'écriture réussie. */
    data class Plan(val target: Entry?, val duplicates: List<Entry>)

    // Motif strict : « backlog.csv » ou « backlog (N).csv » (copie créée par le système ou Drive), rien d'autre.
    private val EXPORT_NAME = Regex("""backlog( \(\d+\))?\.csv""")

    fun isExportName(name: String?): Boolean = name != null && EXPORT_NAME.matches(name)

    /**
     * Choisit la cible parmi les entrées du dossier : le « backlog.csv » au nom exact le plus récent,
     * sinon la copie « backlog (N).csv » la plus récente (le fournisseur a renommé l'export, on ne
     * crée pas un fichier de plus). Tous les autres fichiers au motif sont des doublons.
     * Les fichiers qui ne suivent pas le motif sont ignorés, donc jamais supprimés.
     */
    fun plan(entries: List<Entry>): Plan {
        val exports = entries.filter { isExportName(it.name) }
        val target = exports.filter { it.name == FILE_NAME }.maxByOrNull { it.lastModified }
            ?: exports.maxByOrNull { it.lastModified }
        return Plan(target, exports.filter { it != target })
    }

    /** Liste les fichiers du dossier dont le nom suit le motif d'export. */
    fun listExports(context: Context, treeUri: Uri): List<Entry> {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val entries = mutableListOf<Entry>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (isExportName(name)) {
                    entries += Entry(cursor.getString(0), name, if (cursor.isNull(2)) 0L else cursor.getLong(2))
                }
            }
        }
        return entries
    }

    /** Cible de l'écriture : le fichier existant réutilisé, ou un nouveau « backlog.csv » s'il n'y en a aucun. */
    fun targetUri(context: Context, treeUri: Uri, plan: Plan): Uri {
        plan.target?.let { return DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return DocumentsContract.createDocument(context.contentResolver, parent, MIME, FILE_NAME)
            ?: throw java.io.IOException("Impossible de créer $FILE_NAME dans $treeUri")
    }

    /** Supprime les doublons ; à n'appeler qu'après une écriture réussie. Un échec isolé n'interrompt pas les autres. */
    fun deleteDuplicates(context: Context, treeUri: Uri, duplicates: List<Entry>) {
        duplicates.forEach {
            try {
                DocumentsContract.deleteDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId),
                )
            } catch (_: Exception) {
                // Le prochain passage réessaiera.
            }
        }
    }
}
