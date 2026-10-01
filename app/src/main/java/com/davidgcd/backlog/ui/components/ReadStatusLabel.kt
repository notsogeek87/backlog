package com.davidgcd.backlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.ReadStatus
import com.davidgcd.backlog.ui.theme.Glass

fun ReadStatus.labelRes(): Int = when (this) {
    ReadStatus.TO_READ -> R.string.read_status_to_read
    ReadStatus.READING -> R.string.read_status_reading
    ReadStatus.READ -> R.string.read_status_read
    ReadStatus.ABANDONED -> R.string.read_status_abandoned
}

@Composable
fun ReadStatus.label(): String = stringResource(labelRes())

/** Plural form for the header tiles ("Lus", "Abandonnés"). */
fun ReadStatus.countLabelRes(): Int = when (this) {
    ReadStatus.TO_READ -> R.string.read_stat_to_read
    ReadStatus.READING -> R.string.read_stat_reading
    ReadStatus.READ -> R.string.read_stat_read
    ReadStatus.ABANDONED -> R.string.read_stat_abandoned
}

/** Same colours as the watch statuses (to do = blue, in progress = amber, done = green), grey-pink for dropped. */
fun ReadStatus.tint(): Color = when (this) {
    ReadStatus.TO_READ -> Glass.Blue
    ReadStatus.READING -> Glass.Amber
    ReadStatus.READ -> Glass.Green
    ReadStatus.ABANDONED -> Glass.Pink
}
