package fr.music.passportslot.ui.screens.captcha

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import fr.music.passportslot.util.Constants

/**
 * Screen that loads the real ANTS website in a WebView so the user can solve
 * the LiveIdentity captcha in a genuine browser environment.
 *
 * We inject JavaScript to intercept the XHR response from initCaptchaJWT,
 * which contains the captcha JWT. Once captured, we store it in our
 * CaptchaManager and navigate back.
 *
 * This approach avoids LiveIdentity bot detection that occurs when loading
 * custom HTML pages in a WebView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptchaScreen(
    onCaptchaCompleted: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CaptchaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate back on success
    LaunchedEffect(uiState.captchaSuccess) {
        if (uiState.captchaSuccess) {
            onCaptchaCompleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verification de securite") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("Reessayer")
                        }
                    }
                }
                else -> {
                    // Show the real ANTS website in a WebView
                    AntsWebView(
                        onCaptchaJwtCaptured = { jwt ->
                            viewModel.onCaptchaJwtCaptured(jwt)
                        }
                    )

                    // Loading indicator overlay
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Chargement du site ANTS...")
                                }
                            }
                        }
                    }

                    // Show processing overlay when validating JWT
                    if (uiState.isProcessingToken) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Validation du captcha...")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AntsWebView(
    onCaptchaJwtCaptured: (String) -> Unit
) {
    var pageLoaded by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.databaseEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(false)
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                // Enable cookies (important for LiveIdentity)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                // Add JavaScript interface to receive the JWT from injected JS
                addJavascriptInterface(
                    CaptchaJsBridge(onCaptchaJwtCaptured),
                    "AndroidBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pageLoaded = true
                        // Inject our XHR interceptor to capture initCaptchaJWT response
                        view?.evaluateJavascript(buildInterceptorJs(), null)
                        Log.d("CaptchaWebView", "Page loaded, XHR interceptor injected: $url")
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // Keep navigation within the ANTS site
                        return if (url.contains("rendezvouspasseport.ants.gouv.fr") ||
                            url.contains("captcha.liveidentity.com")) {
                            false // Let WebView handle it
                        } else {
                            true // Block external navigations
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("CaptchaWebView", "JS Console: ${consoleMessage?.message()}")
                        return true
                    }
                }

                // Load the real ANTS website
                loadUrl(Constants.ANTS_WEB_URL)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * JavaScript interface that receives the captured JWT from the injected JS code.
 */
private class CaptchaJsBridge(
    private val onJwtCaptured: (String) -> Unit
) {
    @JavascriptInterface
    fun onCaptchaJwt(jwt: String) {
        Log.d("CaptchaJsBridge", "Captcha JWT captured: ${jwt.take(30)}...")
        if (jwt.isNotEmpty()) {
            onJwtCaptured(jwt)
        }
    }
}

/**
 * Build JavaScript code that intercepts XHR requests to capture the
 * initCaptchaJWT response containing the captcha JWT.
 *
 * The ANTS Angular app makes a POST to /api/initCaptchaJWT which returns
 * {"access_token": "..."}.  We hook into XMLHttpRequest.prototype.open
 * and XMLHttpRequest.prototype.send to monitor responses.
 *
 * We also hook into fetch() as a fallback in case Angular uses fetch.
 */
private fun buildInterceptorJs(): String {
    return """
    (function() {
        if (window.__captchaInterceptorInstalled) return;
        window.__captchaInterceptorInstalled = true;

        console.log('[PassportSlot] Installing captcha JWT interceptor');

        // Hook XMLHttpRequest
        var origOpen = XMLHttpRequest.prototype.open;
        var origSend = XMLHttpRequest.prototype.send;

        XMLHttpRequest.prototype.open = function(method, url) {
            this._captchaUrl = url;
            this._captchaMethod = method;
            return origOpen.apply(this, arguments);
        };

        XMLHttpRequest.prototype.send = function() {
            var self = this;
            if (self._captchaUrl && self._captchaUrl.indexOf('initCaptchaJWT') !== -1) {
                var origOnLoad = self.onload;
                var origOnReadyStateChange = self.onreadystatechange;

                function tryCapture() {
                    if (self.readyState === 4 && self.status === 200) {
                        try {
                            var data = JSON.parse(self.responseText);
                            if (data && data.access_token) {
                                console.log('[PassportSlot] Captured captcha JWT from initCaptchaJWT');
                                AndroidBridge.onCaptchaJwt(data.access_token);
                            }
                        } catch(e) {
                            console.log('[PassportSlot] Failed to parse initCaptchaJWT response: ' + e);
                        }
                    }
                }

                self.onreadystatechange = function() {
                    tryCapture();
                    if (origOnReadyStateChange) {
                        origOnReadyStateChange.apply(self, arguments);
                    }
                };

                self.onload = function() {
                    tryCapture();
                    if (origOnLoad) {
                        origOnLoad.apply(self, arguments);
                    }
                };
            }
            return origSend.apply(this, arguments);
        };

        // Hook fetch() as fallback
        var origFetch = window.fetch;
        if (origFetch) {
            window.fetch = function(input, init) {
                var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                var promise = origFetch.apply(this, arguments);

                if (url.indexOf('initCaptchaJWT') !== -1) {
                    promise.then(function(response) {
                        return response.clone().text().then(function(text) {
                            try {
                                var data = JSON.parse(text);
                                if (data && data.access_token) {
                                    console.log('[PassportSlot] Captured captcha JWT from fetch initCaptchaJWT');
                                    AndroidBridge.onCaptchaJwt(data.access_token);
                                }
                            } catch(e) {
                                console.log('[PassportSlot] Failed to parse fetch initCaptchaJWT response: ' + e);
                            }
                        });
                    });
                }

                return promise;
            };
        }

        console.log('[PassportSlot] Captcha JWT interceptor installed successfully');
    })();
    """.trimIndent()
}
