package com.davidgcd.backlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.GameStatus

@Composable
fun GameStatus.label(): String = stringResource(
    when (this) {
        GameStatus.BACKLOG -> R.string.status_backlog
        GameStatus.PLAYED -> R.string.status_played
        GameStatus.COMPLETED -> R.string.status_completed
    },
)
