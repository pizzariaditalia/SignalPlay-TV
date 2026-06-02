package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class FootballActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_football)

        webView = findViewById(R.id.webViewFutebol)

        // Configurações vitais para rodar perfeitamente na TV
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        
        webView.webViewClient = WebViewClient()

        // Garante que o controle remoto consiga focar na tela
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        // Puxa o arquivo offline de dentro do próprio aplicativo
        webView.loadUrl("file:///android_asset/futebol.html")
    }
}
