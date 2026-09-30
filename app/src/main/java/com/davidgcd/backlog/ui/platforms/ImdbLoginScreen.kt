package com.davidgcd.backlog.ui.platforms

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
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
import com.davidgcd.backlog.data.imdb.ImdbError
import com.davidgcd.backlog.data.imdb.ImdbException
import com.davidgcd.backlog.data.imdb.ImdbSyncService
import com.davidgcd.backlog.data.imdb.WebViewCookieJar
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.ui.components.GlassButton
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ImdbLoginState(
    val verifying: Boolean = false,
    val error: ImdbError? = null,
    val connected: Boolean = false,
)

class ImdbLoginViewModel(
    private val sync: ImdbSyncService,
    private val accounts: LibraryAccountStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ImdbLoginState())
    val state: StateFlow<ImdbLoginState> = _state

    /** Called when the WebView shows a signed-in IMDb page: confirms the session and stores the `ur…` id. */
    fun onSignedIn() {
        if (_state.value.verifying || _state.value.connected) return
        _state.value = ImdbLoginState(verifying = true)
        viewModelScope.launch {
            try {
                val userId = sync.currentUserId() ?: throw ImdbException(ImdbError.NOT_SIGNED_IN)
                accounts.connect(LibraryProviders.IMDB, userId, displayName = null)
                _state.value = ImdbLoginState(connected = true)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val error = (t as? ImdbException)?.error ?: ImdbError.UNKNOWN
                AppLogger.network.warn("IMDb login: verification failed: $error — ${t.message}")
                _state.value = ImdbLoginState(error = error)
            }
        }
    }

    fun retry() {
        _state.value = ImdbLoginState()
    }
}

class ImdbLoginViewModelFactory(
    private val sync: ImdbSyncService,
    private val accounts: LibraryAccountStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == ImdbLoginViewModel::class.java)
        return ImdbLoginViewModel(sync, accounts) as T
    }
}

/**
 * IMDb's own sign-in page (IMDb, Amazon, Google… whichever the user has) in a WebView. Backlog never
 * sees the credentials: it only notices that the WebView holds a signed-in session and then reads
 * the user's own exports with it. The session stays in the WebView's app-private cookie store until
 * the account is disconnected in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImdbLoginScreen(
    viewModel: ImdbLoginViewModel,
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
                title = { Text(stringResource(R.string.imdb_login_title)) },
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
                stringResource(R.string.imdb_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.verifying -> Centered { CircularProgressIndicator(color = Glass.Cyan) }
                    state.error != null -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(stringResource(state.error!!.messageRes()), color = Glass.Text)
                            GlassButton(
                                text = stringResource(R.string.action_retry),
                                onClick = viewModel::retry,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    else -> ImdbWebView(onSignedIn = viewModel::onSignedIn)
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
private fun ImdbWebView(onSignedIn: () -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true // the sign-in pages don't work without it
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        // Back on a normal imdb.com page (not the sign-in / Amazon auth ones) with a session cookie = signed in.
                        val onImdb = url != null && url.contains("imdb.com") && !url.contains("/registration") && !url.contains("/ap/")
                        if (onImdb && WebViewCookieJar.hasSignInCookie()) onSignedIn()
                    }
                }
                loadUrl(SIGN_IN_URL)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

private const val SIGN_IN_URL = "https://www.imdb.com/registration/signin"

fun ImdbError.messageRes(): Int = when (this) {
    ImdbError.NOT_SIGNED_IN -> R.string.imdb_error_not_signed_in
    ImdbError.UNAVAILABLE -> R.string.imdb_error_unavailable
    ImdbError.UNKNOWN -> R.string.imdb_error_unknown
}
