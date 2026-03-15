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
 * Screen that displays a WebView for the user to solve the LiveIdentity captcha.
 *
 * The approach:
 * 1. Load a minimal HTML page that includes the LiveIdentity antibot script
 * 2. Initialize the captcha widget with antibotId/requestId from the ANTS API
 * 3. User solves the captcha (QUESTION type)
 * 4. Poll for the captcha token in the hidden input field
 * 5. Once obtained, call initCaptchaJWT + validateCaptchaJWT via the ViewModel
 * 6. Navigate back with success
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
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Chargement du captcha...")
                    }
                }
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
                uiState.antibotId != null -> {
                    CaptchaWebView(
                        antibotId = uiState.antibotId!!,
                        requestId = uiState.requestId!!,
                        onCaptchaTokenObtained = { token ->
                            viewModel.onCaptchaTokenObtained(token)
                        }
                    )

                    // Show processing overlay
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
private fun CaptchaWebView(
    antibotId: String,
    requestId: String,
    onCaptchaTokenObtained: (String) -> Unit
) {
    var tokenPollingActive by remember { mutableStateOf(true) }

    val captchaHtml = remember(antibotId, requestId) {
        buildCaptchaHtml(antibotId, requestId)
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                // Enable cookies
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Start polling for the captcha token
                        if (tokenPollingActive) {
                            pollForToken(view, onCaptchaTokenObtained) {
                                tokenPollingActive = false
                            }
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("CaptchaWebView", "JS: ${consoleMessage?.message()}")
                        return true
                    }
                }

                loadDataWithBaseURL(
                    Constants.ANTS_WEB_URL,
                    captchaHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun pollForToken(
    webView: WebView?,
    onTokenObtained: (String) -> Unit,
    onStop: () -> Unit
) {
    webView ?: return

    val jsCode = """
        (function() {
            var el = document.getElementById('li-antibot-desktop-token');
            if (el && el.value && el.value.length > 10) {
                return el.value;
            }
            return '';
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsCode) { result ->
        val token = result?.trim()?.removeSurrounding("\"") ?: ""
        if (token.isNotEmpty() && token.length > 10) {
            Log.d("CaptchaWebView", "Captcha token obtained: ${token.take(20)}...")
            onStop()
            onTokenObtained(token)
        } else {
            // Poll again after 1 second
            webView.postDelayed({
                pollForToken(webView, onTokenObtained, onStop)
            }, 1000)
        }
    }
}

private fun buildCaptchaHtml(antibotId: String, requestId: String): String {
    return """
    <!DOCTYPE html>
    <html lang="fr">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Verification</title>
        <style>
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                margin: 0;
                padding: 16px;
                background: #f5f5f5;
                display: flex;
                flex-direction: column;
                align-items: center;
            }
            h3 {
                color: #000091;
                margin-bottom: 8px;
            }
            p {
                color: #666;
                font-size: 14px;
                margin-bottom: 24px;
                text-align: center;
            }
            #li-antibot-desktop {
                width: 100%;
                max-width: 400px;
                min-height: 200px;
            }
            input[type="hidden"] {
                display: none;
            }
        </style>
    </head>
    <body>
        <h3>Verification de securite</h3>
        <p>Veuillez resoudre le captcha ci-dessous pour continuer</p>
        <div id="li-antibot-desktop"></div>
        <input type="hidden" id="li-antibot-desktop-token" value="">
        
        <script src="${Constants.LIVE_IDENTITY_SCRIPT_URL}"></script>
        <script>
            try {
                LI_ANTIBOT.loadAntibot({
                    antibotId: "$antibotId",
                    requestId: "$requestId",
                    spKey: "${Constants.LIVE_IDENTITY_SP_KEY}",
                    cookie: true,
                    principalCaptcha: "QUESTION",
                    alternativeCaptcha: "AUDIO",
                    url: "${Constants.LIVE_IDENTITY_URL}/captcha",
                    locale: "fr"
                }, 'li-antibot-desktop');
            } catch(e) {
                document.body.innerHTML += '<p style="color:red">Erreur: ' + e.message + '</p>';
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}
