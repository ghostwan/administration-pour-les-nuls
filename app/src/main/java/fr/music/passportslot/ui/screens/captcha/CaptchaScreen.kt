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
    // Track whether we already captured a JWT to avoid double-firing
    var jwtCaptured by remember { mutableStateOf(false) }

    val safeCaptureCallback: (String) -> Unit = remember {
        { jwt: String ->
            if (!jwtCaptured) {
                jwtCaptured = true
                onCaptchaJwtCaptured(jwt)
            }
        }
    }

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
                    CaptchaJsBridge(safeCaptureCallback),
                    "AndroidBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // Inject interceptor as early as possible, before Angular bootstraps
                        view?.evaluateJavascript(buildInterceptorJs(), null)
                        Log.d("CaptchaWebView", "Page starting, XHR interceptor injected early: $url")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Re-inject in case the early injection was too early
                        view?.evaluateJavascript(buildInterceptorJs(), null)
                        Log.d("CaptchaWebView", "Page finished, XHR interceptor re-injected: $url")
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // Keep navigation within the ANTS site and LiveIdentity
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
 * Build JavaScript code that intercepts XHR and fetch requests to capture the
 * initCaptchaJWT response containing the captcha JWT.
 *
 * Uses multiple strategies:
 * 1. Hook XMLHttpRequest.prototype.open/send with addEventListener('load')
 *    (Angular HttpClient uses addEventListener, NOT onload/onreadystatechange)
 * 2. Hook fetch() as fallback
 * 3. Poll for WebSocket messages containing antibot_token in localStorage/sessionStorage
 * 4. Monitor the Angular app's HTTP traffic by hooking XMLHttpRequest at the
 *    prototype level before Angular bootstraps
 *
 * The script is idempotent (safe to inject multiple times).
 */
private fun buildInterceptorJs(): String {
    return """
    (function() {
        if (window.__captchaInterceptorInstalled) return;
        window.__captchaInterceptorInstalled = true;
        window.__captchaJwtCaptured = false;

        console.log('[PassportSlot] Installing captcha JWT interceptor v2');

        function reportJwt(jwt) {
            if (window.__captchaJwtCaptured) return;
            if (!jwt || jwt.length < 10) return;
            window.__captchaJwtCaptured = true;
            console.log('[PassportSlot] Captured captcha JWT: ' + jwt.substring(0, 30) + '...');
            try {
                AndroidBridge.onCaptchaJwt(jwt);
            } catch(e) {
                console.log('[PassportSlot] Failed to call AndroidBridge: ' + e);
            }
        }

        // === Strategy 1: Hook XMLHttpRequest ===
        var origOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.__psUrl = (typeof url === 'string') ? url : String(url);
            this.__psMethod = method;
            return origOpen.apply(this, arguments);
        };

        var origSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function() {
            var self = this;
            var url = self.__psUrl || '';

            if (url.indexOf('initCaptchaJWT') !== -1) {
                console.log('[PassportSlot] Detected XHR to initCaptchaJWT');

                // Use addEventListener - this is what Angular HttpClient uses
                self.addEventListener('load', function() {
                    console.log('[PassportSlot] XHR initCaptchaJWT completed, status=' + self.status);
                    if (self.status === 200) {
                        try {
                            var data = JSON.parse(self.responseText);
                            if (data && data.access_token) {
                                reportJwt(data.access_token);
                            }
                        } catch(e) {
                            console.log('[PassportSlot] Failed to parse XHR response: ' + e);
                        }
                    }
                });

                // Also hook readystatechange as extra safety
                self.addEventListener('readystatechange', function() {
                    if (self.readyState === 4 && self.status === 200) {
                        try {
                            var data = JSON.parse(self.responseText);
                            if (data && data.access_token) {
                                reportJwt(data.access_token);
                            }
                        } catch(e) {}
                    }
                });
            }

            // Also capture validateCaptchaJWT to log success/failure
            if (url.indexOf('validateCaptchaJWT') !== -1) {
                console.log('[PassportSlot] Detected XHR to validateCaptchaJWT');
                self.addEventListener('load', function() {
                    console.log('[PassportSlot] validateCaptchaJWT response: status=' + self.status);
                });
            }

            return origSend.apply(this, arguments);
        };

        // === Strategy 2: Hook fetch() ===
        var origFetch = window.fetch;
        if (origFetch) {
            window.fetch = function(input, init) {
                var url = '';
                if (typeof input === 'string') {
                    url = input;
                } else if (input && input.url) {
                    url = input.url;
                } else if (input instanceof Request) {
                    url = input.url;
                }

                var promise = origFetch.apply(this, arguments);

                if (url.indexOf('initCaptchaJWT') !== -1) {
                    console.log('[PassportSlot] Detected fetch to initCaptchaJWT');
                    promise.then(function(response) {
                        if (response.ok) {
                            response.clone().text().then(function(text) {
                                try {
                                    var data = JSON.parse(text);
                                    if (data && data.access_token) {
                                        reportJwt(data.access_token);
                                    }
                                } catch(e) {
                                    console.log('[PassportSlot] Failed to parse fetch response: ' + e);
                                }
                            });
                        }
                    }).catch(function(e) {
                        console.log('[PassportSlot] fetch initCaptchaJWT failed: ' + e);
                    });
                }

                return promise;
            };
        }

        // === Strategy 3: Periodic polling of Angular app state ===
        // The Angular app may store the token in memory. We can try to
        // find it by checking for known DOM elements or global variables.
        var pollCount = 0;
        var maxPolls = 300; // 5 minutes at 1s intervals
        var pollInterval = setInterval(function() {
            if (window.__captchaJwtCaptured || pollCount >= maxPolls) {
                clearInterval(pollInterval);
                return;
            }
            pollCount++;

            // Check if Angular stored a token we can grab
            // The ANTS app uses localStorage sometimes
            try {
                var keys = Object.keys(localStorage);
                for (var i = 0; i < keys.length; i++) {
                    var val = localStorage.getItem(keys[i]);
                    if (val && val.indexOf('eyJ') === 0 && val.length > 50) {
                        // Looks like a JWT
                        console.log('[PassportSlot] Found potential JWT in localStorage key: ' + keys[i]);
                        // Don't auto-report localStorage JWTs - they might be auth tokens
                    }
                }
            } catch(e) {}

            // Check for the captcha token input that LiveIdentity creates
            try {
                var tokenEl = document.getElementById('li-antibot-desktop-token');
                if (tokenEl && tokenEl.value && tokenEl.value.length > 20 &&
                    tokenEl.value.indexOf('Blacklisted') === -1) {
                    console.log('[PassportSlot] LiveIdentity token field has value (len=' + tokenEl.value.length + ')');
                    // Don't report this - it's the raw captcha token, not the JWT.
                    // But log it so we know the captcha was solved.
                }
            } catch(e) {}
        }, 1000);

        console.log('[PassportSlot] Captcha JWT interceptor v2 installed');
    })();
    """.trimIndent()
}
