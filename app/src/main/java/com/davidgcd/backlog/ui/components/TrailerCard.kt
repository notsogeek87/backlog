package com.davidgcd.backlog.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.davidgcd.backlog.R
import com.davidgcd.backlog.model.Trailer

/**
 * "Bande-annonce", watched right in the page: the thumbnail first (nothing is loaded from YouTube until
 * the user taps play), then the embedded player. The title says VF, or VO when no French version exists.
 * "Ouvrir dans YouTube" stays as a way out for videos whose owner forbids embedding.
 */
@Composable
fun TrailerCard(trailer: Trailer) {
    val context = LocalContext.current
    var playing by remember(trailer.youtubeKey) { mutableStateOf(false) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (trailer.isFrench) R.string.trailer_title_fr else R.string.trailer_title_vo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val shape = RoundedCornerShape(12.dp)
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Color.Black)) {
                if (playing) {
                    YouTubePlayer(trailer.youtubeKey, modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = "https://i.ytimg.com/vi/${trailer.youtubeKey}/hqdefault.jpg",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClickLabel = stringResource(R.string.trailer_play)) { playing = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.trailer_play),
                            tint = Color.White,
                            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(14.dp),
                        )
                    }
                }
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailer.url))) }) {
                Text(stringResource(R.string.trailer_open_youtube))
            }
        }
    }
}

/**
 * YouTube's iframe player in a WebView, autoplaying (the user just tapped play). The page is loaded with an
 * https base URL: without a referrer YouTube refuses to embed (error 153). Fullscreen goes through the
 * activity's window; the view is destroyed with the composable so audio stops when leaving the page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(videoId: String, modifier: Modifier) {
    val context = LocalContext.current
    val webView = remember(videoId) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            val html = """
                <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style></head>
                <body><iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&hl=fr&cc_lang_pref=fr&cc_load_policy=1"
                allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>
            """.trimIndent()
            loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "utf-8", null)
        }
    }
    val fullscreen = remember(webView) { FullscreenHost(context) }
    webView.webChromeClient = fullscreen
    DisposableEffect(webView) {
        onDispose {
            fullscreen.exit()
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

/** Puts the player's fullscreen view over the whole activity window and takes it back out. */
private class FullscreenHost(private val context: Context) : WebChromeClient() {
    private var view: View? = null
    private var callback: CustomViewCallback? = null

    override fun onShowCustomView(v: View, cb: CustomViewCallback) {
        val activity = context.findActivity() ?: return cb.onCustomViewHidden()
        exit()
        (activity.window.decorView as FrameLayout).addView(
            v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view = v
        callback = cb
    }

    override fun onHideCustomView() = exit()

    fun exit() {
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        callback?.onCustomViewHidden()
        view = null
        callback = null
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
