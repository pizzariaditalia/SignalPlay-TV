package com.tv.signalplay

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Carrega a tela preta vazia e não faz mais nada.
        setContentView(R.layout.activity_main)
    }
}
