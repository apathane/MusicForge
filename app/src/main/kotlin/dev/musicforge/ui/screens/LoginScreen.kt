package dev.musicforge.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import com.metrolist.innertube.YouTube
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber

private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

/** Reads the page's ytcfg: the same values the official web client sends with every request. */
private const val READ_CONFIG = """(function(){var c=window.yt&&window.yt.config_;
return c?JSON.stringify({v:c.VISITOR_DATA||'',d:c.DATASYNC_ID||'',a:String(c.SESSION_INDEX||0)}):'';})()"""

/**
 * Google sign-in in a WebView. Once the page lands on music.youtube.com with a SAPISID
 * cookie, the session (cookie, visitor data, data-sync id, auth user) is saved under the same
 * keys Metrolist and ConvX use, and YouTubeSession picks it up from settings.
 */
@Composable
fun LoginScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var capture by remember { mutableStateOf<Job?>(null) }

    suspend fun eval(view: WebView, js: String): String {
        val result = CompletableDeferred<String>()
        view.evaluateJavascript(js) { result.complete(it ?: "") }
        // evaluateJavascript returns a JSON-encoded value: a quoted string here.
        return withTimeoutOrNull(2_000) { result.await() }?.let { raw ->
            runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull()
        }.orEmpty()
    }

    fun captureSession(view: WebView) {
        if (capture?.isActive == true) return
        capture = scope.launch {
            status = "Signing in…"
            repeat(30) {
                val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
                val config = eval(view, READ_CONFIG)
                if ("SAPISID=" in cookie && config.isNotEmpty()) {
                    val json = JSONObject(config)
                    val visitor = json.optString("v")
                    if (visitor.isNotEmpty()) {
                        YouTube.cookie = cookie
                        YouTube.visitorData = visitor
                        YouTube.dataSyncId = json.optString("d").substringBefore("||").ifEmpty { null }
                        YouTube.authUser = json.optString("a").filter(Char::isDigit).ifEmpty { "0" }
                        val account = YouTube.accountInfo().getOrElse {
                            Timber.w(it, "account info failed after sign-in")
                            status = "Signed in, but couldn't read the account. Try again."
                            return@launch
                        }
                        context.graph.settings.edit { p ->
                            p[Keys.InnerTubeCookie] = cookie
                            p[Keys.VisitorData] = visitor
                            p[Keys.DataSyncId] = json.optString("d").substringBefore("||")
                            p[Keys.InnerTubeAuthUser] = YouTube.authUser
                            p[Keys.AccountName] = account.name
                            p[Keys.AccountEmail] = account.email.orEmpty()
                            p[Keys.AccountChannelHandle] = account.channelHandle.orEmpty()
                        }
                        CookieManager.getInstance().flush()
                        // A new or different account: the old account's snapshots must not decide removals.
                        context.graph.accountSync.forgetSnapshots()
                        context.graph.scope.launch { context.graph.accountSync.sync() }
                        // Reload so everything already on screen (Home's feed, Library) asks again as this account.
                        (context as? android.app.Activity)?.recreate() ?: onDone()
                        return@launch
                    }
                }
                delay(500)
            }
            status = "Couldn't finish signing in. Check your connection and try again."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(ForgeIcons.Back, "Back", onDone)
            Text("Sign in to YouTube Music", style = MaterialTheme.typography.titleMedium)
        }
        status?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 10.dp).size(16.dp), color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(Modifier.weight(1f).navigationBarsPadding()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // why: JavaScript is required for the Google sign-in page (ytcfg reading, cookie capture).
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            // Google's sign-in can hand off to intent:// links (account chooser, passkeys).
                            // A WebView can't load those and the flow stalls there, so open them as Android
                            // intents, the way Metrolist does; ordinary pages stay in the WebView.
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                                val scheme = request.url.scheme
                                if (scheme == "http" || scheme == "https") return false
                                if (scheme == "intent") {
                                    runCatching {
                                        android.content.Intent.parseUri(request.url.toString(), android.content.Intent.URI_INTENT_SCHEME).apply {
                                            addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                                            component = null
                                            selector = null
                                        }.let(ctx::startActivity)
                                    }
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                val uri = url?.let(android.net.Uri::parse)
                                if (uri?.scheme == "https" && uri.host == "music.youtube.com") captureSession(view)
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
            )
        }
    }
}

