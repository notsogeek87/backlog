package com.davidgcd.backlog.ui.ranking

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Le « déjà à la bonne place ? » du glisser-déposer, sans rien de graphique pour rester testable :
 * quand le centre de la ligne tirée passe le centre d'une voisine, on les échange. Renvoie l'index de la voisine
 * (dans [rows], des lignes de hauteurs et de positions connues) ou null.
 */
object ReorderMath {
    /** [rows] : (offset, taille) de chaque ligne visible, dans l'ordre d'affichage ; [dragged] est l'index de la ligne tirée. */
    fun swapTarget(rows: List<Pair<Int, Int>>, dragged: Int, dragOffset: Float): Int? {
        val (offset, size) = rows.getOrNull(dragged) ?: return null
        val center = offset + dragOffset + size / 2f
        rows.forEachIndexed { index, (o, s) ->
            if (index == dragged) return@forEachIndexed
            val targetCenter = o + s / 2f
            val passed = if (index > dragged) center > targetCenter else center < targetCenter
            if (passed && center > o && center < o + s) return index
        }
        return null
    }

    /**
     * Après l'échange, la ligne tirée change de place dans la liste : son décalage de pouce se corrige de la taille de la
     * voisine pour qu'elle reste sous le doigt.
     */
    fun compensate(dragOffset: Float, targetSize: Int, movedDown: Boolean): Float = dragOffset + if (movedDown) -targetSize else targetSize
}

/** État d'un glisser-déposer dans une `LazyColumn` dont les lignes ont des clés `String`. */
@Stable
class DragReorderState(private val listState: LazyListState) {
    var draggingKey: String? by mutableStateOf(null)
        private set
    var offsetY: Float by mutableFloatStateOf(0f)
        private set

    fun start(key: String) {
        draggingKey = key
        offsetY = 0f
    }

    /** Fait suivre la ligne au doigt ; appelle [onSwap] (clé tirée, clé de la voisine) quand elle passe une voisine. */
    fun drag(dy: Float, onSwap: (String, String) -> Unit) {
        val key = draggingKey ?: return
        offsetY += dy
        val rows = listState.layoutInfo.visibleItemsInfo.filter { it.key is String }
        val draggedIndex = rows.indexOfFirst { it.key == key }
        if (draggedIndex < 0) return
        val targetIndex = ReorderMath.swapTarget(rows.map { it.offset to it.size }, draggedIndex, offsetY) ?: return
        val target: LazyListItemInfo = rows[targetIndex]
        onSwap(key, target.key as String)
        offsetY = ReorderMath.compensate(offsetY, target.size, movedDown = targetIndex > draggedIndex)
    }

    /** Garde la ligne sous le doigt quand la liste défile d'elle-même (bord de l'écran). */
    fun scrolledBy(consumed: Float) {
        offsetY += consumed
    }

    /** Vrai si le doigt est près du haut (-1) ou du bas (1) de la zone visible, 0 sinon : il faut faire défiler. */
    fun edgeDirection(margin: Int = 120): Int {
        val key = draggingKey ?: return 0
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0
        val top = dragged.offset + offsetY
        val bottom = top + dragged.size
        return when {
            top < info.viewportStartOffset + margin -> -1
            bottom > info.viewportEndOffset - margin -> 1
            else -> 0
        }
    }

    fun end() {
        draggingKey = null
        offsetY = 0f
    }
}
