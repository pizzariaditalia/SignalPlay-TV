package com.signalplay.tv

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Esta linha é a mágica que pega o design XML e joga na tela da TV
        setContentView(R.layout.activity_main)
    }
}
