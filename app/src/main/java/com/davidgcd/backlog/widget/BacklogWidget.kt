package com.davidgcd.backlog.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.davidgcd.backlog.BacklogApplication
import com.davidgcd.backlog.MainActivity
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Medium
import com.davidgcd.backlog.ui.home.HomeItem
import com.davidgcd.backlog.ui.home.HomeState
import com.davidgcd.backlog.ui.home.HomeViewModel
import com.davidgcd.backlog.util.DetailLink
import com.davidgcd.backlog.util.ReleaseDateFormatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Widget d'écran d'accueil : ce qui est en cours et la prochaine sortie, tous médias confondus. Un toucher sur une ligne
 * ouvre la fiche ; ailleurs, l'app. Les données sont lues dans Room à chaque mise à jour (l'app la déclenche en passant
 * en arrière-plan ; Android la rafraîchit aussi de lui-même toutes les 30 minutes).
 */
class BacklogWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as BacklogApplication
        val state = withContext(Dispatchers.IO) {
            HomeViewModel.build(app.repository.allGames(), app.movieRepository.allMovies(), app.bookRepository.allBooks(), System.currentTimeMillis())
        }
        provideContent { WidgetContent(context, state) }
    }
}

class BacklogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BacklogWidget()
}

private val Night = Color(0xFF0A1220)
private val Cyan = Color(0xFF22D3EE)
private val TextMain = Color(0xFFF5F7FF)
private val TextMuted = Color(0xFFB4BCD0)

private fun openItem(context: Context, item: HomeItem): Intent {
    val link = when (item.medium) {
        Medium.GAME -> item.key.toLongOrNull()?.let(DetailLink::Game)
        Medium.MOVIE -> DetailLink.Movie(item.key)
        Medium.BOOK -> DetailLink.Book(item.key)
    }
    return Intent(Intent.ACTION_VIEW, link?.let { Uri.parse(DetailLink.url(it)) })
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

@androidx.compose.runtime.Composable
private fun WidgetContent(context: Context, state: HomeState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Night))
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))),
    ) {
        Text(
            context.getString(R.string.app_name),
            style = TextStyle(color = ColorProvider(Cyan), fontWeight = FontWeight.Bold, fontSize = 13.sp),
        )
        Spacer(GlanceModifier.height(6.dp))
        val inProgress = state.inProgress.take(3)
        val next = state.upcoming.firstOrNull()
        if (inProgress.isEmpty() && next == null) {
            Text(context.getString(R.string.widget_empty), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 12.sp))
        }
        if (inProgress.isNotEmpty()) {
            Text(context.getString(R.string.home_continue), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
            inProgress.forEach { item ->
                Text(
                    item.title,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(TextMain), fontWeight = FontWeight.Medium, fontSize = 14.sp),
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(actionStartActivity(openItem(context, item))),
                )
            }
        }
        if (next != null) {
            Spacer(GlanceModifier.height(6.dp))
            Text(context.getString(R.string.widget_next_release), style = TextStyle(color = ColorProvider(TextMuted), fontSize = 11.sp))
            Text(
                "${next.item.title} · ${ReleaseDateFormatting.format(next.epochSeconds).orEmpty()}",
                maxLines = 1,
                style = TextStyle(color = ColorProvider(TextMain), fontWeight = FontWeight.Medium, fontSize = 14.sp),
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(actionStartActivity(openItem(context, next.item))),
            )
        }
    }
}
