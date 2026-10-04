package com.davidgcd.backlog.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Trailer

/** "Bande-annonce": opens the YouTube video, in French when one exists (otherwise flagged as original version). */
@Composable
fun TrailerCard(trailer: Trailer) {
    val context = LocalContext.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.trailer_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            GlassButton(
                text = stringResource(if (trailer.isFrench) R.string.trailer_watch_fr else R.string.trailer_watch_vo),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailer.url))) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
