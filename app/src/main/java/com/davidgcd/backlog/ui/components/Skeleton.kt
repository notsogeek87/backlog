package com.davidgcd.backlog.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.davidgcd.backlog.ui.theme.Glass

/** Lignes fantômes qui pulsent pendant un chargement : la page garde sa forme au lieu d'un spinner au milieu du vide. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 6) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp).alpha(alpha).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    val shape = RoundedCornerShape(10.dp)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp, 85.dp).clip(shape).background(Glass.GlassStrong))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(shape).background(Glass.GlassStrong))
                Box(Modifier.width(120.dp).height(12.dp).clip(shape).background(Glass.GlassTop))
                Box(Modifier.width(80.dp).height(12.dp).clip(shape).background(Glass.GlassTop))
            }
        }
    }
}
