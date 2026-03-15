package fr.music.passportslot.ui.screens.captcha

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import fr.music.passportslot.util.Constants
import kotlinx.coroutines.delay

/**
 * Screen that loads the real ANTS website in a WebView so the user can solve
 * the LiveIdentity captcha in a genuine browser environment.
 *
 * We inject JavaScript to intercept the XHR response from initCaptchaJWT,
 * which contains the captcha JWT. Once captured, we store it in our
 * CaptchaManager and navigate back.
 *
 * If the ANTS site creates a WebSocket search without showing a captcha,
 * we detect this and auto-navigate back (captcha was not required after all).
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

    // Navigate back on success (JWT captured or captcha not needed)
    LaunchedEffect(uiState.captchaSuccess) {
        if (uiState.captchaSuccess) {
            onCaptchaCompleted()
        }
    }

    // Navigate back when captcha was detected as not required
    LaunchedEffect(uiState.captchaNotRequired) {
        if (uiState.captchaNotRequired) {
            onNavigateBack()
        }
    }

    // Status message for the info bar
    val statusMessage = when {
        uiState.isProcessingToken -> "Validation du captcha en cours..."
        uiState.captchaDetected -> "Captcha detecte - veuillez le resoudre ci-dessous"
        uiState.pageLoaded -> "Naviguez sur le site pour declencher le captcha"
        else -> "Chargement du site ANTS..."
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Status info bar
            Surface(
                color = when {
                    uiState.isProcessingToken -> MaterialTheme.colorScheme.primaryContainer
                    uiState.captchaDetected -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isProcessingToken || !uiState.pageLoaded) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (uiState.captchaDetected) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    } else {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Main content
            Box(modifier = Modifier.weight(1f)) {
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
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
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
                            },
                            onWebSocketDetected = {
                                viewModel.onWebSocketDetected()
                            },
                            onCaptchaWidgetDetected = {
                                viewModel.onCaptchaWidgetDetected()
                            },
                            onPageLoaded = {
                                viewModel.onPageLoaded()
                            }
                        )

                        // Show processing overlay when validating JWT
                        if (uiState.isProcessingToken) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AntsWebView(
    onCaptchaJwtCaptured: (String) -> Unit,
    onWebSocketDetected: () -> Unit,
    onCaptchaWidgetDetected: () -> Unit,
    onPageLoaded: () -> Unit
) {
    // Track whether we already captured a JWT to avoid double-firing
    var jwtCaptured by remember { mutableStateOf(false) }
    var wsDetected by remember { mutableStateOf(false) }
    var captchaDetected by remember { mutableStateOf(false) }

    val safeCaptureCallback: (String) -> Unit = remember {
        { jwt: String ->
            if (!jwtCaptured) {
                jwtCaptured = true
                onCaptchaJwtCaptured(jwt)
            }
        }
    }

    val safeWsCallback: () -> Unit = remember {
        {
            if (!wsDetected) {
                wsDetected = true
                onWebSocketDetected()
            }
        }
    }

    val safeCaptchaDetectedCallback: () -> Unit = remember {
        {
            if (!captchaDetected) {
                captchaDetected = true
                onCaptchaWidgetDetected()
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
                    CaptchaJsBridge(
                        onJwtCaptured = safeCaptureCallback,
                        onWebSocketDetected = safeWsCallback,
                        onCaptchaWidgetDetected = safeCaptchaDetectedCallback
                    ),
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
                        onPageLoaded()
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
 * JavaScript interface that receives events from the injected JS code.
 */
private class CaptchaJsBridge(
    private val onJwtCaptured: (String) -> Unit,
    private val onWebSocketDetected: () -> Unit,
    private val onCaptchaWidgetDetected: () -> Unit
) {
    @JavascriptInterface
    fun onCaptchaJwt(jwt: String) {
        Log.d("CaptchaJsBridge", "Captcha JWT captured: ${jwt.take(30)}...")
        if (jwt.isNotEmpty()) {
            onJwtCaptured(jwt)
        }
    }

    @JavascriptInterface
    fun onWebSocketCreated() {
        Log.d("CaptchaJsBridge", "ANTS WebSocket search detected - captcha may not be needed")
        onWebSocketDetected()
    }

    @JavascriptInterface
    fun onCaptchaWidgetVisible() {
        Log.d("CaptchaJsBridge", "LiveIdentity captcha widget detected in DOM")
        onCaptchaWidgetDetected()
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
 * 3. Hook WebSocket constructor to detect when search starts (captcha not needed)
 * 4. Poll DOM for LiveIdentity captcha widget visibility
 *
 * The script is idempotent (safe to inject multiple times).
 */
private fun buildInterceptorJs(): String {
    return """
    (function() {
        if (window.__captchaInterceptorInstalled) return;
        window.__captchaInterceptorInstalled = true;
        window.__captchaJwtCaptured = false;
        window.__wsNotified = false;
        window.__captchaWidgetNotified = false;

        console.log('[PassportSlot] Installing captcha JWT interceptor v3');

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

        function reportWebSocket() {
            if (window.__wsNotified) return;
            window.__wsNotified = true;
            console.log('[PassportSlot] Notifying Android of WebSocket creation');
            try {
                AndroidBridge.onWebSocketCreated();
            } catch(e) {
                console.log('[PassportSlot] Failed to call AndroidBridge.onWebSocketCreated: ' + e);
            }
        }

        function reportCaptchaWidget() {
            if (window.__captchaWidgetNotified) return;
            window.__captchaWidgetNotified = true;
            console.log('[PassportSlot] Notifying Android of captcha widget');
            try {
                AndroidBridge.onCaptchaWidgetVisible();
            } catch(e) {
                console.log('[PassportSlot] Failed to call AndroidBridge.onCaptchaWidgetVisible: ' + e);
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

        // === Strategy 3: Hook WebSocket constructor ===
        // Detect when the ANTS site creates a search WebSocket.
        // If this fires, it means the search started without needing captcha.
        var OrigWebSocket = window.WebSocket;
        window.WebSocket = function(url, protocols) {
            console.log('[PassportSlot] WebSocket created: ' + url);
            if (url && url.indexOf('SlotsFromPositionStreaming') !== -1) {
                console.log('[PassportSlot] ANTS search WebSocket detected — captcha passed or not needed');
                reportWebSocket();
            }
            if (protocols !== undefined) {
                return new OrigWebSocket(url, protocols);
            }
            return new OrigWebSocket(url);
        };
        window.WebSocket.prototype = OrigWebSocket.prototype;
        window.WebSocket.CONNECTING = OrigWebSocket.CONNECTING;
        window.WebSocket.OPEN = OrigWebSocket.OPEN;
        window.WebSocket.CLOSING = OrigWebSocket.CLOSING;
        window.WebSocket.CLOSED = OrigWebSocket.CLOSED;

        // === Strategy 4: Poll for captcha widget and localStorage tokens ===
        var pollCount = 0;
        var maxPolls = 300; // 5 minutes at 1s intervals
        var pollInterval = setInterval(function() {
            if (window.__captchaJwtCaptured || pollCount >= maxPolls) {
                clearInterval(pollInterval);
                return;
            }
            pollCount++;

            // Check for the LiveIdentity captcha widget in the DOM
            try {
                var captchaContainer = document.querySelector('.li-antibot-container, #li-antibot-desktop, [class*="antibot"]');
                if (captchaContainer) {
                    var rect = captchaContainer.getBoundingClientRect();
                    if (rect.width > 0 && rect.height > 0) {
                        reportCaptchaWidget();
                    }
                }
                // Also check for iframe-based captcha
                var captchaIframe = document.querySelector('iframe[src*="liveidentity"], iframe[src*="captcha"]');
                if (captchaIframe) {
                    reportCaptchaWidget();
                }
            } catch(e) {}

            // Check if Angular stored a token we can grab
            try {
                var keys = Object.keys(localStorage);
                for (var i = 0; i < keys.length; i++) {
                    var val2 = localStorage.getItem(keys[i]);
                    if (val2 && val2.indexOf('eyJ') === 0 && val2.length > 50) {
                        console.log('[PassportSlot] Found potential JWT in localStorage key: ' + keys[i]);
                    }
                }
            } catch(e) {}
        }, 1000);

        console.log('[PassportSlot] Captcha JWT interceptor v3 installed');
    })();
    """.trimIndent()
}
