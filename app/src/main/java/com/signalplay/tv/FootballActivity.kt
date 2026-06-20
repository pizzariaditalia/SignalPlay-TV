package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class FootballActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        actionBar?.hide()
        
        // NOVIDADE: Tela cheia
        TvNavigationUtils.aplicarModoImersivo(this)
        
        setContentView(R.layout.activity_football)

        webView = findViewById(R.id.webViewFutebol)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        
        webView.webViewClient = WebViewClient()

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.loadUrl("file:///android_asset/futebol.html")
    }

    // NOVIDADE: Garante que a barra preta não volte
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            TvNavigationUtils.aplicarModoImersivo(this)
        }
    }
}
