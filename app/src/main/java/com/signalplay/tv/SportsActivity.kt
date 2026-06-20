package com.signalplay.tv

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar

class SportsActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        actionBar?.hide()
        
        // NOVIDADE: Chama o modo tela cheia agressivo
        TvNavigationUtils.aplicarModoImersivo(this)
        
        setContentView(R.layout.activity_sports)

        webView = findViewById(R.id.webViewInterno)
        val progress = findViewById<ProgressBar>(R.id.progressEsportes)

        val url = intent.getStringExtra("URL_LIGA") ?: "https://m.sofascore.com/pt/"

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val currentUrl = request?.url?.toString() ?: ""
                if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")) {
                    return false 
                }
                return true 
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.visibility = View.GONE
            }
        }
        
        webView.webChromeClient = WebChromeClient()
        
        val headers = mutableMapOf<String, String>()
        headers["X-Requested-With"] = ""
        webView.loadUrl(url, headers)
        
        webView.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
                webView.goBack() 
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // NOVIDADE: Garante que a barra preta não volte ao minimizar e maximizar o app
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            TvNavigationUtils.aplicarModoImersivo(this)
        }
    }
}
