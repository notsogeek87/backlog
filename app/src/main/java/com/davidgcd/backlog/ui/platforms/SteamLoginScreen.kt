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
import com.davidgcd.backlog.data.library.LibraryAccountStore
import com.davidgcd.backlog.data.library.LibraryError
import com.davidgcd.backlog.data.library.LibraryProviders
import com.davidgcd.backlog.data.library.steam.SteamAuthService
import com.davidgcd.backlog.data.library.steam.SteamOpenId
import com.davidgcd.backlog.data.library.toLibraryException
import com.davidgcd.backlog.ui.components.glassTopAppBarColors
import com.davidgcd.backlog.ui.theme.Glass
import com.davidgcd.backlog.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SteamLoginState(
    val verifying: Boolean = false,
    val error: LibraryError? = null,
    val connected: Boolean = false,
)

class SteamLoginViewModel(
    private val auth: SteamAuthService,
    private val accounts: LibraryAccountStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SteamLoginState())
    val state: StateFlow<SteamLoginState> = _state

    /** Called once with the URL Steam redirected to. Stores only the verified SteamID64 + persona name. */
    fun onCallback(url: String) {
        if (_state.value.verifying) return
        _state.value = SteamLoginState(verifying = true)
        viewModelScope.launch {
            try {
                val profile = auth.completeLogin(url)
                accounts.connect(LibraryProviders.STEAM, profile.steamId, profile.displayName)
                _state.value = SteamLoginState(connected = true)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val error = t.toLibraryException()
                if (error.error != LibraryError.CANCELLED) AppLogger.network.warn("Steam login failed: ${error.message}")
                _state.value = SteamLoginState(error = error.error)
            }
        }
    }

    fun retry() {
        _state.value = SteamLoginState()
    }
}

class SteamLoginViewModelFactory(
    private val auth: SteamAuthService,
    private val accounts: LibraryAccountStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == SteamLoginViewModel::class.java)
        return SteamLoginViewModel(auth, accounts) as T
    }
}

/**
 * Steam's own OpenID page in a WebView. JavaScript is required by that page; no JS bridge is exposed
 * to it, and the WebView's cookies are wiped on exit so the Steam session doesn't outlive this screen.
 * Backlog only ever sees the redirect URL (a signed claim), never the credentials typed on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamLoginScreen(
    viewModel: SteamLoginViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.connected) { if (state.connected) onConnected() }
    // A cancelled sign-in just returns to Settings; real failures stay on screen with a message.
    LaunchedEffect(state.error) { if (state.error == LibraryError.CANCELLED) onBack() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = glassTopAppBarColors(),
                title = { Text(stringResource(R.string.steam_login_title)) },
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
                stringResource(R.string.steam_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.verifying -> Centered { CircularProgressIndicator(color = Glass.Cyan) }
                    state.error != null -> Centered {
                        Text(
                            stringResource(state.error!!.messageRes()),
                            color = Glass.Text,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> SteamWebView(onCallback = viewModel::onCallback)
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
private fun SteamWebView(onCallback: (String) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true // Steam's login page doesn't work without it
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (SteamOpenId.isCallback(url)) {
                            onCallback(url)
                            return true // never actually load the return address
                        }
                        return false
                    }
                }
                loadUrl(SteamOpenId.loginUrl())
            }
        },
        onRelease = { webView ->
            CookieManager.getInstance().removeAllCookies(null)
            webView.stopLoading()
            webView.destroy()
        },
    )
    // Also drop the session if the composable leaves before the view is released.
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().removeAllCookies(null) } }
}
