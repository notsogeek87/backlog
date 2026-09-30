package com.davidgcd.backlog.ui.platforms

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davidgcd.backlog.R
import com.davidgcd.backlog.data.tmdb.TmdbError
import com.davidgcd.backlog.data.tmdb.TmdbException
import com.davidgcd.backlog.data.tmdb.TmdbSyncService
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TmdbLoginState(
    /** True while the token is requested / the session is opened. */
    val working: Boolean = true,
    /** The TMDB approval page to show, once the request token exists. */
    val approvalUrl: String? = null,
    val error: TmdbError? = null,
    val connected: Boolean = false,
)

class TmdbLoginViewModel(
    private val sync: TmdbSyncService,
) : ViewModel() {
    private val _state = MutableStateFlow(TmdbLoginState())
    val state: StateFlow<TmdbLoginState> = _state

    private var requestToken: String? = null

    init {
        start()
    }

    /** Step 1: a request token, then the TMDB page where the user signs in and approves. */
    fun start() {
        _state.value = TmdbLoginState(working = true)
        viewModelScope.launch {
            try {
                if (!sync.isConfigured) throw TmdbException(TmdbError.NOT_CONFIGURED)
                val (token, url) = sync.startLogin(REDIRECT_URL)
                requestToken = token
                _state.value = TmdbLoginState(working = false, approvalUrl = url)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    /** Called when TMDB redirected back to [REDIRECT_URL]: [approved] tells whether the user allowed access. */
    fun onRedirect(approved: Boolean) {
        val token = requestToken
        if (!approved || token == null) {
            _state.value = TmdbLoginState(working = false, error = TmdbError.NOT_SIGNED_IN)
            return
        }
        requestToken = null
        _state.value = TmdbLoginState(working = true)
        viewModelScope.launch {
            try {
                sync.finishLogin(token)
                _state.value = TmdbLoginState(working = false, connected = true)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    private fun fail(t: Throwable) {
        val error = (t as? TmdbException)?.error ?: TmdbError.UNKNOWN
        AppLogger.network.warn("TMDB login failed: $error — ${t.message}")
        _state.value = TmdbLoginState(working = false, error = error)
    }

    companion object {
        /** Never actually loaded: the WebView intercepts it (same trick as the Steam login). */
        const val REDIRECT_URL = "https://backlog.invalid/tmdb-approved"
    }
}

class TmdbLoginViewModelFactory(
    private val sync: TmdbSyncService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == TmdbLoginViewModel::class.java)
        return TmdbLoginViewModel(sync) as T
    }
}

/**
 * TMDB's own approval page (sign in with a TMDB account, then "Approve") in a WebView. Backlog never
 * sees the credentials: it only gets a session TMDB grants once the user approves, kept in the app's
 * no-backup storage. The WebView's cookies are wiped on exit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmdbLoginScreen(
    viewModel: TmdbLoginViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.connected) { if (state.connected) onConnected() }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Glass.Text,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.tmdb_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.tmdb_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.working -> Centered { CircularProgressIndicator(color = Glass.Cyan) }
                    state.error != null -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(stringResource(state.error!!.messageRes()), color = Glass.Text)
                            GlassButton(
                                text = stringResource(R.string.action_retry),
                                onClick = viewModel::start,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    state.approvalUrl != null -> TmdbWebView(url = state.approvalUrl!!, onRedirect = viewModel::onRedirect)
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TmdbWebView(url: String, onRedirect: (approved: Boolean) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true // TMDB's sign-in page doesn't work without it
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        if (target.toString().startsWith(TmdbLoginViewModel.REDIRECT_URL)) {
                            onRedirect(target.getQueryParameter("approved") == "true")
                            return true // never actually load the return address
                        }
                        return false
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { webView ->
            CookieManager.getInstance().removeAllCookies(null)
            webView.stopLoading()
            webView.destroy()
        },
    )
    // Also drop the TMDB web session if the composable leaves before the view is released.
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().removeAllCookies(null) } }
}

fun TmdbError.messageRes(): Int = when (this) {
    TmdbError.NOT_CONFIGURED -> R.string.tmdb_error_not_configured
    TmdbError.NOT_SIGNED_IN -> R.string.tmdb_error_not_signed_in
    TmdbError.UNAVAILABLE -> R.string.tmdb_error_unavailable
    TmdbError.UNKNOWN -> R.string.tmdb_error_unknown
}
