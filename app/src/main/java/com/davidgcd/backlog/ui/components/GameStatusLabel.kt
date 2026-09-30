package com.davidgcd.backlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.GameStatus
import com.davidgcd.backlog.ui.theme.Glass

fun GameStatus.labelRes(): Int = when (this) {
    GameStatus.BACKLOG -> R.string.status_backlog
    GameStatus.PLAYED -> R.string.status_played
    GameStatus.COMPLETED -> R.string.status_completed
}

@Composable
fun GameStatus.label(): String = stringResource(labelRes())

/** One colour per status, distinct from the cyan platform / purple genre / amber archived badges. */
fun GameStatus.tint(): Color = when (this) {
    GameStatus.BACKLOG -> Glass.Blue
    GameStatus.PLAYED -> Glass.Pink
    GameStatus.COMPLETED -> Glass.Green
}
