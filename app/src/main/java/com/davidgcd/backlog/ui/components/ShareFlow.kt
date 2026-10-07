package com.davidgcd.backlog.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.share.ShareLinkService
import kotlinx.coroutines.launch

/** Ce que l'écran a besoin de savoir du partage : un partage est-il en cours, et comment le lancer. */
class ShareFlow(val sharing: Boolean, val start: () -> Unit)

/**
 * Le partage de bibliothèque commun aux trois onglets : (pseudo si besoin,) choix « Ma librairie / Cet onglet »,
 * lien public, repli sur le texte brut. Seule la façon de partager l'onglet ([shareTab]) change d'un média à l'autre.
 *
 * [ownerName] / [saveOwnerName] : renseignés, le partage commence par demander le pseudo affiché sur la page publique.
 */
@Composable
fun rememberShareFlow(
    tabHintRes: Int,
    publishLibraryLink: suspend (String) -> String?,
    shareTab: suspend (Context) -> Unit,
    ownerName: (suspend () -> String)? = null,
    saveOwnerName: (suspend (String) -> Unit)? = null,
): ShareFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var showScope by remember { mutableStateOf(false) }
    var showOwnerPrompt by remember { mutableStateOf(false) }

    val runTab: () -> Unit = {
        scope.launch {
            sharing = true
            try {
                shareTab(context)
            } finally {
                sharing = false
            }
        }
    }
    val runLibrary: () -> Unit = {
        scope.launch {
            sharing = true
            try {
                val header = context.getString(R.string.share_library_header)
                // No server reachable: fall back to this tab's plain text rather than sharing nothing.
                val link = publishLibraryLink(header)
                if (link == null) {
                    shareTab(context)
                } else {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "$header\n$link")
                    }
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_library_chooser)))
                }
            } finally {
                sharing = false
            }
        }
    }

    if (showScope) {
        ShareScopeDialog(
            tabHintRes = tabHintRes,
            onLibrary = { showScope = false; runLibrary() },
            onTab = { showScope = false; runTab() },
            onDismiss = { showScope = false },
        )
    }

    if (showOwnerPrompt) {
        var name by remember { mutableStateOf("") }
        val dismiss = { showOwnerPrompt = false }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.share_owner_prompt_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ShareLinkService.MAX_OWNER_LENGTH) },
                    label = { Text(stringResource(R.string.settings_share_name_title)) },
                    supportingText = { Text(stringResource(R.string.share_owner_prompt_message)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    dismiss()
                    scope.launch {
                        saveOwnerName?.invoke(name)
                        showScope = true
                    }
                }) { Text(stringResource(R.string.share_owner_prompt_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dismiss(); showScope = true }) { Text(stringResource(R.string.share_owner_prompt_skip)) }
            },
        )
    }

    return ShareFlow(
        sharing = sharing,
        start = {
            scope.launch {
                if (ownerName != null && ownerName().isBlank()) showOwnerPrompt = true else showScope = true
            }
        },
    )
}

/** Partage rapide d'une fiche : le lien d'app qui l'ouvre chez le contact. */
fun shareDetailLink(context: Context, title: String, link: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\n$link")
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
