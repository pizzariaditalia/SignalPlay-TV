package com.signalplay.tv

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView

object TvNavigationUtils {

    // 1. MODO IMERSIVO TOTAL (Remove a barra preta do topo)
    fun aplicarModoImersivo(activity: Activity) {
        val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        
        activity.window.decorView.systemUiVisibility = flags

        // Garante que a barra não volte se o usuário clicar com mouse/air mouse
        activity.window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                activity.window.decorView.systemUiVisibility = flags
            }
        }
    }

    // 2 e 3. FLUIDEZ DE SCROLL E TRAVA DE FOCO
    fun configurarPrateleira(recycler: RecyclerView) {
        recycler.setHasFixedSize(true)
        recycler.setItemViewCacheSize(20)

        // Adiciona um "Imã" para o banner parar perfeitamente centralizado
        if (recycler.onFlingListener == null) {
            LinearSnapHelper().attachToRecyclerView(recycler)
        }

        // Trava absoluta do controle remoto (D-Pad)
        recycler.setOnKeyListener { view, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                // Se apertar para a DIREITA e a lista não puder mais rolar, CANCELA a ação
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !view.canScrollHorizontally(1)) {
                    return@setOnKeyListener true 
                }
                // Se apertar para a ESQUERDA e a lista estiver no começo, CANCELA a ação
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !view.canScrollHorizontally(-1)) {
                    return@setOnKeyListener true 
                }
            }
            false
        }
    }
}
