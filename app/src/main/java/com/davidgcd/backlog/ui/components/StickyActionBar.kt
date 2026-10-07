package com.davidgcd.backlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.ui.theme.Glass

/**
 * Barre d'action collée en bas d'une fiche : l'action principale (« Ajouter ») reste visible sans faire
 * défiler la page. Fond opaque pour que le contenu qui passe dessous ne gêne pas la lecture.
 */
@Composable
fun StickyActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Glass.Bg)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { content() }
}
