package dev.musicforge.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.metrolist.innertube.models.AccountInfo
import dev.musicforge.data.prefs.Keys
import dev.musicforge.graph
import dev.musicforge.ui.components.Artwork
import dev.musicforge.ui.components.ForgeIcons
import dev.musicforge.ui.components.IconAction
import dev.musicforge.ui.components.PillButton
import dev.musicforge.ui.designsystem.signalSurface
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

/** The cookie, ytcfg and account read from the page; saved to settings only when the user confirms. */
private data class CapturedSession(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
    val account: AccountInfo,
)

/**
 * Google sign-in in a WebView. When the page lands on music.youtube.com with a SAPISID cookie,
 * the session (cookie, visitor data, data-sync id, auth user and account info) is read and shown
 * with a "Use this account" button. Saving to settings and starting sync happen only after that
 * tap: auto-saving here recreated the activity while the login route was still on the back stack,
 * reopening LoginScreen and looping forever. After saving, the route is popped first; Home and
 * Library already observe the settings flow, so no recreate() is needed.
 */
@Composable
fun LoginScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<CapturedSession?>(null) }
    // Guards: capture tracks the running probe (Job?.isActive), committing blocks a second save or
    // a re-probe after the user taps Use this account or navigates away.
    var capture by remember { mutableStateOf<Job?>(null) }
    var committing by remember { mutableStateOf(false) }

    suspend fun eval(view: WebView, js: String): String {
        val result = CompletableDeferred<String>()
        view.evaluateJavascript(js) { result.complete(it ?: "") }
        // evaluateJavascript returns a JSON-encoded value: a quoted string here.
        return withTimeoutOrNull(2_000) { result.await() }?.let { raw ->
            runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull()
        }.orEmpty()
    }

    fun probeSession(view: WebView) {
        if (capture?.isActive == true) return
        if (session != null || committing) return
        capture = scope.launch {
            status = "Checking sign-in…"
            repeat(30) {
                if (session != null || committing) return@launch
                val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
                val config = eval(view, READ_CONFIG)
                if ("SAPISID=" in cookie && config.isNotEmpty()) {
                    val json = JSONObject(config)
                    val visitor = json.optString("v")
                    if (visitor.isNotEmpty()) {
                        // Set the YouTube client so accountInfo() works; YouTubeSession will replay
                        // these from settings once the user confirms and the values are persisted.
                        YouTube.cookie = cookie
                        YouTube.visitorData = visitor
                        YouTube.dataSyncId = json.optString("d").substringBefore("||").ifEmpty { null }
                        YouTube.authUser = json.optString("a").filter(Char::isDigit).ifEmpty { "0" }
                        val account = YouTube.accountInfo().getOrElse {
                            Timber.w(it, "account info failed after sign-in")
                            status = "Signed in, but couldn't read the account. Try again."
                            return@launch
                        }
                        session = CapturedSession(
                            cookie = cookie,
                            visitorData = visitor,
                            dataSyncId = json.optString("d").substringBefore("||"),
                            authUser = YouTube.authUser,
                            account = account,
                        )
                        status = null
                        return@launch
                    }
                }
                delay(500)
            }
            if (session == null && !committing) status = "Couldn't finish signing in. Check your connection and try again."
        }
    }

    fun useAccount() {
        if (committing) return
        val sess = session ?: return
        committing = true
        status = "Saving account…"
        scope.launch {
            try {
                context.graph.settings.edit { p ->
                    p[Keys.InnerTubeCookie] = sess.cookie
                    p[Keys.VisitorData] = sess.visitorData
                    p[Keys.DataSyncId] = sess.dataSyncId
                    p[Keys.InnerTubeAuthUser] = sess.authUser
                    p[Keys.AccountName] = sess.account.name
                    p[Keys.AccountEmail] = sess.account.email.orEmpty()
                    p[Keys.AccountChannelHandle] = sess.account.channelHandle.orEmpty()
                }
                CookieManager.getInstance().flush()
                // A new or different account: the old account's snapshots must not decide removals.
                context.graph.accountSync.forgetSnapshots()
                context.graph.scope.launch { context.graph.accountSync.sync() }
                // Pop the login route first; Home and Library watch the settings flow, so no
                // recreate() — that reopened LoginScreen and looped.
                onDone()
            } catch (e: Exception) {
                Timber.e(e, "failed to save signed-in account")
                committing = false
                status = "Couldn't save the account. Try again."
            }
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
        session?.let { sess ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .signalSurface(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val thumb = sess.account.thumbnailUrl
                    if (thumb != null) Artwork(thumb, 56.dp, corner = 28.dp)
                    Column(Modifier.padding(start = if (thumb != null) 12.dp else 0.dp)) {
                        Text(sess.account.name, style = MaterialTheme.typography.titleMedium)
                        sess.account.email?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                PillButton("Use this account", { useAccount() }, modifier = Modifier.fillMaxWidth(), enabled = !committing)
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
                                if (uri?.scheme == "https" && uri.host == "music.youtube.com") probeSession(view)
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
            )
        }
    }
}
