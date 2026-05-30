package com.signalplay.tv

import android.app.Activity
import android.os.Bundle

class HomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Carrega o layout Google TV que criamos
        setContentView(R.layout.activity_home)
    }
}
