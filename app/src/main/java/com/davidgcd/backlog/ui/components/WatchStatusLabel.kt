package com.davidgcd.backlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.TitleKind
import com.davidgcd.backlog.model.WatchStatus
import com.davidgcd.backlog.ui.theme.Glass

fun WatchStatus.labelRes(): Int = when (this) {
    WatchStatus.TO_WATCH -> R.string.watch_status_to_watch
    WatchStatus.WATCHING -> R.string.watch_status_watching
    WatchStatus.WATCHED -> R.string.watch_status_watched
}

@Composable
fun WatchStatus.label(): String = stringResource(labelRes())

fun WatchStatus.tint(): Color = when (this) {
    WatchStatus.TO_WATCH -> Glass.Blue
    WatchStatus.WATCHING -> Glass.Amber
    WatchStatus.WATCHED -> Glass.Green
}

fun TitleKind.labelRes(): Int = when (this) {
    TitleKind.MOVIE -> R.string.kind_movie
    TitleKind.SERIES -> R.string.kind_series
}

@Composable
fun TitleKind.label(): String = stringResource(labelRes())
